---
title: Normalizer — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, normalizer]
related: ["components/component-specs.md", "reference/bill-attributes.md", "reference/embedding-benchmark.md"]
---

# Normalizer (Python, 수집)

> **런타임 변경(D35):** 구현 런타임이 Python → **Spring(Boot 4.0 + Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]] · [[spring-migration|버전 변경점]]). 본 문서의 역할·입출력·동작·결정 의도는 그대로 유효하며, Python 인터페이스 초안은 **포팅 사양**으로 유지된다.


> `RawBill` → **표준 `Bill`+`Article[]`** 변환. 조문·부칙·신구조문대비표·위임조항 파싱과 `revision` 계산. 관련: [[component-specs]] §1·§4 #3 · [[bill-attributes]]

## 역할
출처별 원형(`RawBill`)을 도메인 표준 모델로 정규화한다. 특히 **신구조문대비표**를 1급으로 파싱해 [[embedding-benchmark|벤치 시나리오 A]]의 정답쌍 원천을 만든다.

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `RawBill` | 출처 원형(커넥터 산출) |
| 출력 | `Bill`(+`Article[]`, `Addendum[]`) | [[component-specs]] §1 스키마 |

## 파라미터 (설정)
| 파라미터 | 예 | 설명 |
|---|---|---|
| `revision_fields` | title,fullText,articles,effectiveDate,stage,baselineLawId | 해시 대상(분석 영향 필드만) |
| `article_pattern` | 제·조·항·호 규칙 | 조문 파싱 정규식/규칙 |
| `extract_oldnew` | true | 신구조문대비표 추출 on/off |

## 동작
1. 필드 매핑: `billNo,title,proposerType,committee,stage,proposeDate...`
2. **조문 파싱**: 본문 → `Article[]`(no/title/text/`changeType`)
3. **부칙 분해**: 시행일·경과조치·적용례·특례 → `Addendum[]`; `effectiveDate`/`effectiveRule` 추출
4. **신구조문대비표 파싱**: 현행↔개정 짝 → `Article.oldNewTable` (★ 벤치 정답쌍)
5. **위임조항 감지**: "~는 대통령령으로 정한다" → `delegationClauses`
6. **`changeType` 판정**(신설/개정/삭제/이동)
7. **`revision` 계산**: `sha256(canonical(revision_fields))[:16]`; `lastSeen` 별도

오류/엣지: 파싱 실패 조문 → `changeType:"없음"`+원문 보존+결손 플래그(드롭 금지).

## 인터페이스 (Python 초안)
```python
class Normalizer:
    def normalize(self, raw: RawBill) -> Bill: ...
    def _parse_articles(self, text) -> list[Article]: ...
    def _parse_old_new_table(self, raw) -> dict[str, OldNew]: ...
    def _compute_revision(self, bill) -> str: ...
```

## 구조 결정 의도 (왜 이렇게)
- **수집↔해석 분리.** Normalizer는 *출처를 모른다* — `RawBill`만 받음. 그래야 출처가 늘어도 정규화 로직 불변.
- **신구조문대비표를 1급으로.** 이게 (a) MVP diff의 1차 소스, (b) 임베딩 벤치 시나리오 A의 *무료 정답쌍*. 그래서 파싱을 핵심 동작으로 승격([[decision-log|D26·D33]]).
- **`revision`은 분석영향 필드 해시.** 단계·시행일·본문 변동만 캐시를 무효화하고, 행정 메타 변동은 무시 → 불필요 재분석 방지([[decision-log|D16]]).
- **결손 보존(드롭 금지).** 파싱 실패해도 원문을 남겨 그라운딩(인용) 가능성을 지킴.
- BillFacts(🟡C 파생)는 여기서 **만들지 않음** — 그건 [[AnalysisEngine]]의 Layer A 책임. Normalizer는 🟢A+🔵B 사실만.

## 의존 / 관련
- 입력: [[SourceConnector]]
- 출력 적재: Bill Store(RDB)
- 후속 소비: [[AnalysisEngine]], [[RAGIndexer]](요약·BillFacts 임베딩)
