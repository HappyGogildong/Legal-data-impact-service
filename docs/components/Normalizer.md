---
title: Normalizer — 컴포넌트 설계
status: Draft
version: 0.2
date: 2026-08-02
tags: [component, pipeline, normalizer]
related: ["components/component-specs.md", "reference/law-attributes.md", "components/SourceConnector.md"]
---

# Normalizer (Spring, 정규화)

> **v0.1 → v0.2 (D42, 2026-08-02):** 입출력이 `RawBill → Bill` 에서 **`RawLaw → Law`** 로 바뀌었다. 본문 획득 갭(D38)이 해소돼 **Phase 1/2 분리가 불필요**해졌고, **신구조문대비표 파싱은 폐기**됐다. 런타임은 Spring(D35).

> `RawLaw` → **표준 `Law` + `Article[]` + `Addendum[]`** 변환. 조문 병합·부칙 필터·시행규칙 추출·`revision` 계산. 관련: [[component-specs]] §1.1 · [[law-attributes]] · [[SourceConnector]] §MVP 본문 경로

## 역할

국가법령정보 응답 원형(`RawLaw`)을 도메인 표준 모델로 정규화한다. **출처 API의 기벽을 여기서 끝낸다** — 하류(diff·색인·분석)는 `Law`만 보고 `Map<String,Object>` 를 다시 열지 않는다. `RawLaw → Law` 변환이 Anti-Corruption Layer 의 안쪽 절반이다.

## 입력 / 출력

| | 타입 | 설명 |
|---|---|---|
| 입력 | `RawLaw` (본문 포함) | `fetchPending`/`fetchCurrent` 산출. 필드 정의는 [[SourceConnector]] |
| 출력 | `Law` (+ `Article[]`, `Addendum[]`) | [[component-specs]] §1.1 스키마 |

> **본문 갭 해소(D42).** v0.1에서는 `RawBill` 에 본문이 없어 동작 2~5를 실행할 수 없었고 Phase 1/2로 쪼개야 했다. 시행 대기 법령은 API가 **조문 전문·부칙·개정문·제개정이유를 모두 주므로** 그 분리가 사라졌다.

## 파라미터 (설정)

| 파라미터 | 예 | 설명 |
|---|---|---|
| `revision-fields` | title, fullText, articles, effectiveDate, promulgateNo, baselineLawId | 해시 대상(분석 영향 필드만) |
| `delegation-patterns` | "대통령령으로 정한다", "총리령으로", "부령으로" | 위임조항 감지 |

## 동작

1. **헤더 매핑** — `기본정보` → `lawId · mst · title · amendKind · lawType · ministry · promulgateDate · promulgateNo · effectiveDate`
2. **조문 조립** — `조문단위[]` → `Article[]`. **`조문내용` + `항 → 호 → 목` 재귀 병합**(⚠️ 함정: `조문내용`만 읽으면 제목 줄만 나온다). `조문여부 != "조문"` 인 항목(장·절 제목)은 `isArticle=false` 로 구분
3. **변경 플래그 매핑** — `조문변경여부` → `Article.changed`, `조문이동이전/이후` → `movedFrom`/`movedTo`, `조문시행일자` → `articleEffectiveDate`
4. **`changeType` 판정** — `changed=Y` + 이동 필드 조합으로 신설/개정/삭제/이동 결정
5. **부칙 필터·분해** — `부칙단위[]` 중 **`부칙공포번호 == promulgateNo`** 만 취해 `Addendum[]` 로. 종류(시행일/경과조치/적용례/특례) 분류
6. **시행 규칙 추출** — 부칙 제1조에서 `effectiveRule` 문자열, 단서조항("다만 …") 유무로 `enforcementType`(즉시/유예/단계적) 판정
7. **위임조항 감지** — "~는 대통령령으로 정한다" → `delegationClauses`. 하류에서 `uncertainties` 로 승계
8. **`revision` 계산** — `sha256(canonical(revision-fields))[:16]`. `lastSeen` 은 해시에 넣지 않는다

**오류/엣지:** 파싱 실패 조문은 **드롭하지 않는다** — `changeType:"없음"` + 원문 보존 + 결손 플래그. 인용 가능성을 잃지 않기 위해서다.

## 인터페이스 (Java, `com.lia.core.pipeline.normalize`) — 구현 완료 ✅

```java
public class Normalizer {
    Law normalize(RawLaw raw);                       // 헤더 + 조문 + 부칙 + 위임 + revision
    List<Article> parseArticles(Map<String,Object> lawRoot);
    List<Addendum> parseAddenda(Map<String,Object> lawRoot, String promulgateNo);
    EffectiveRule parseEffectiveRule(List<Addendum> addenda);   // (text, EnforcementType)
    List<String> detectDelegations(List<Article> articles);
    String computeRevision(Law law);
}
```

> **`LawEnvelope` 정리 완료.** `changedArticles`·`addendaOf` 를 걷어냈다 — "이번 개정으로 바뀐 조문", "이번 개정의 부칙"은 법령 도메인 개념이지 JSON 파싱이 아니다. 지금은 **`Law.changedArticles()`** 와 **`Normalizer.parseAddenda`** 가 담당하고, `LawEnvelope` 에는 순수 파싱(`extractRows`·`articles`·`addenda`·`text`·`date`·`checkError`)만 남았다. `LawEnvelope.addenda()` 는 **이력 전체**를 그대로 준다.

### `Law` 도메인 질의

```java
law.changedArticles()   // 이번 개정으로 바뀐 조문 — 분석 대상 선별
law.realArticles()      // 조문여부='조문' 만 (장·절 제목 제외)
law.effectiveClause()   // 부칙 시행일 조항
law.ref()               // "LAW:{lawId}@{effectiveDate}" — 시행중본은 "@" 없음
law.sourceId(article)   // "LAW:{lawId}@{efYd}:art:{no}"
law.fullText()          // 실조문 병합(파생값, 저장 필드 아님)
```

`lawId`·`effectiveDate` 는 생성자에서 필수로 강제한다 — 전자는 시행중↔시행예정 연결키, 후자는 `ActionPlan` 기한 산출의 근거다.

## 실측 검증 (2026-08-02, 주택법 `LAW:001809@2026-08-04`)

```
조문 137개(실조문 125, 변경 6) · 부칙 3개 · 위임 237건 · revision c4426c2f1ab8a2fe
시행규칙: "이 법은 공포 후 6개월이 경과한 날부터 시행한다. 다만, 제57조제2항제7호의
          개정규정은 공포한 날부터 시행한다." → 단계적
기준선(시행중본): 주택법 조문 125개, revision 66fc6aff3e2ec627
```

- 실조문 125개 **전부 본문이 채워졌다**(빈 조문 0) — 항/호/목 병합이 실제 응답에서 동작
- 부칙 이력 전체에서 이번 개정분 3개 조항만 남았다
- `enforcementType` 이 단서조항으로 `단계적` 판정
- **위임 237건**은 예상보다 많다 — 주택법이 하위법령 위임이 많은 법률이라 그렇다. `uncertainties` 로 전량 노출하면 과할 수 있어 **변경 조문의 위임만 추릴지는 Layer A 파생 단계에서 결정**한다(후속)

## 구조 결정 의도 (왜 이렇게)

- **수집↔해석 분리.** Normalizer는 *출처 API를 모른다* — `RawLaw` 만 받는다. 출처가 늘어도 정규화 로직은 불변이고, 반대로 API 응답 형식이 바뀌어도 커넥터와 여기서 끝난다.
- **신구조문대비표 파싱 폐기(D42).** v0.1에서는 이게 MVP diff의 1차 소스이자 벤치 정답쌍이라 핵심 동작이었다. 지금은 **시행중본과 시행예정본이 동일 스키마로 조문 전문을 주므로** 조문번호로 직접 대조하면 되고, 자구 변경 근거는 `개정문` 이 권위 있게 제공한다. HWP 파서가 통째로 불필요해졌다.
- **`changed` 플래그를 1급으로.** 실측(주택법) 조문 137개 중 변경 6개 — diff·분석 대상을 20분의 1로 줄이는 비용 레버다. **`개정문` 정규식 파싱은 금지**(타법 인용 오탐·벌칙 조문 누락 실측).
- **부칙은 공포번호로 거른다.** API가 제정 이후 이력 전체(실측 42개)를 주므로, 필터 없이 쓰면 10년 전 경과조치를 이번 개정 내용으로 오인한다.
- **`revision` 은 분석영향 필드 해시.** 본문·시행일·공포번호 변동만 캐시를 무효화하고 행정 메타 변동은 무시한다([[decision-log|D16]]).
- **결손 보존(드롭 금지).** 파싱 실패해도 원문을 남겨 그라운딩 가능성을 지킨다.
- **`LawFacts`(🟡C 파생)는 여기서 만들지 않는다.** 그건 Layer A 파생 단계의 책임이고, Normalizer는 🟢A 사실만 다룬다.

## 의존 / 관련

- 입력: [[SourceConnector|LawConnector]]
- 출력 적재: Law Store(RDB)
- 후속 소비: Diff Builder(변경 조문 대조) · [[RAGIndexer]](요약 임베딩) · [[AnalysisEngine]]
