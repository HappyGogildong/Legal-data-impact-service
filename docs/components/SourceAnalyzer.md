---
title: SourceAnalyzer — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, resolver]
related: ["components/component-specs.md", "architecture/v0.5-bill-discovery.md"]
---

# SourceAnalyzer (Python, 식별)

> **런타임 변경(D35):** 구현 런타임이 Python → **Spring(Boot 4.0 + Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]] · [[spring-migration|버전 변경점]]). 본 문서의 역할·입출력·동작·결정 의도는 그대로 유효하며, Python 인터페이스 초안은 **포팅 사양**으로 유지된다.


> 사용자 입력 → **어떤 법안인가**를 해소(resolve). 분석가가 아니라 *식별자(resolver)*. 관련: [[component-specs]] §4 #2 · [[v0.5-bill-discovery]] §3.3

## 역할
법안명·의안번호·URL·**모호 자연어**를 받아 실재 법안으로 해소한다. **신뢰 출처에서 확인 안 되면 분석하지 않는다(fail-closed)** — 입력 *내용*을 사실로 받지 않음.

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `{ type: billNo\|title\|text\|url, value }` | url/text는 본문 추출 후 |
| 출력 | `ResolutionResult{ state, resolved?\|candidates?\|similar?, message? }` | 4상태 |

해소 4상태: `RESOLVED` / `AMBIGUOUS` / `NOT_FOUND_YET` / `UNVERIFIED`.

## 파라미터 (설정)
| 파라미터 | 기본 | 설명 |
|---|---|---|
| `fuzzy_threshold` | 0.x | 정확/퍼지 매칭 임계 |
| `semantic_top_k` | 5 | 법안 의미검색 후보 수 |
| `semantic_min_score` | 0.x | 후보 채택 최소 유사도 |
| `on_demand_source` | true | Store miss 시 출처 직접 질의 |

## 동작
1. 엔티티 추출(LLM): 법안명·의안번호·키워드·**주제·효과**
2. Bill Store 정확/퍼지 매칭
3. 약하면 → **법안 의미검색**([[RAGIndexer|법안 네임스페이스]], BillFacts·요약 임베딩) → 후보
4. Store/출처 miss → **on-demand 신뢰 출처 질의**(미등록 vs 부재 판별)
5. 4상태 판정: 단일=RESOLVED, 다수=AMBIGUOUS(명확화), 형식상 유효하나 부재=NOT_FOUND_YET, 매칭 없음/모순=UNVERIFIED

## 인터페이스 (Python 초안)
```python
class SourceAnalyzer:
    def resolve(self, inp: UserInput) -> ResolutionResult: ...
    def _extract_entities(self, text) -> Entities: ...      # LLM
    def _semantic_search(self, query) -> list[Candidate]: ...  # Vector Index(법안 ns)
```

## 구조 결정 의도 (왜 이렇게)
- **resolver ≠ analyzer.** LLM은 "어떤 법안인가"만, 데이터는 항상 신뢰 출처 원문 → 뉴스·소문이 분석으로 둔갑하지 않음(그라운딩).
- **fail-closed.** 확인 안 되면 분석 거부. 4상태로 **미등록(NOT_FOUND_YET)과 허위(UNVERIFIED)를 구분**해 안내 문구를 다르게([[decision-log|D23]]).
- **정확매칭 + 의미검색 2단계.** 모호 plain text("…서로 경계하게 만드는 법안")는 정확매칭이 안 되므로 *탐색용 임베딩*으로 후보화([[decision-log|D30]]).
- 분석 게이트는 [[AnalysisEngine|Pipeline]] 앞단 — `RESOLVED`만 통과.

## 의존 / 관련
- 의존: Bill Store, Vector Index(법안 탐색, [[RAGIndexer]]), (확장) [[SourceConnector]]
- 게이트 소비: AnalysisPipeline(#8) 0단계
