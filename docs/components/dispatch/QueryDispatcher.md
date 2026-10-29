---
title: Query Dispatcher — 컴포넌트 설계 · 구현 착수
status: Draft
version: 0.5
date: 2026-10-29
tags: [component, pipeline, dispatcher, orchestration]
related: ["components/component-specs.md", "components/plan/QueryPlanner.md", "components/analyze/AnalysisEngine.md", "components/store/LawStore.md", "components/discover/LawDiscovery.md", "architecture/v0.9-nl-query-planner.md"]
---

# QueryDispatcher (Spring, 차원 라우팅·조립)

> 검증된 `AnalysisQuery`를 **차원(QueryType)별 핸들러로 라우팅**하고 결과를 조립한다. 계획(`QueryPlanner`)과 실행(`AnalysisEngine`)을 잇는 오케스트레이터(D47). 관련: [[QueryPlanner]] · [[AnalysisEngine]] · [[component-specs]] §4 #8~#10 · [[v0.9-nl-query-planner]] §5

## Responsibility

- **담당**: target 해석(정본 정확조회) · `types` 순회 라우팅 · 부분성공 조립(`DispatchResult`).
- **담당 안 함**: 프롬프트 빌드·LLM 호출·인용검증(→ 핸들러가 위임한 [[AnalysisEngine]]) · 자연어 번역·해소(→ [[QueryPlanner]]) · 벡터 검색(→ [[LawDiscovery]] 전용, D56·D70).

> **차원은 사용자 선택 모드가 아니다(D46).** 플래너가 질의에서 고른 내부 분해 + 그라운딩 가드레일. 한 질의가 여러 차원을 요구할 수 있어(포괄질문) `types`는 집합이고, 결과는 **부분성공**으로 조립된다.

## Collaborators

- **포트**: `LawSource`(정본·기준선 정확조회, [[LawStore]] 구현) · `DimensionHandlerRegistry`(차원→Layer A 핸들러) · `PersonalizationQuota`(하루 개인화 한도, `PersonalizationUsageStore` 구현).
- **주입**: `PersonalizedHandler`(Layer B 전담, 레지스트리 밖) · `LawDiscovery`(LOOKUP 전담, 레지스트리 밖, D70).
- **간접**: 핸들러들이 [[AnalysisEngine]]에 추론을 위임. `SummaryHandler` 는 쉬운 요약 저장본(`LawSummaries`, D67)을 먼저 본다.
- **외부 시스템**: 없음(직접). 정본 읽기는 `LawSource` 뒤로 격리.

## 구조 (컴포넌트)

| 클래스 | 역할 |
|---|---|
| `QueryDispatcher` | 오케스트레이터 — target 해석 → 차원 라우팅 → `DispatchResult` 조립. 흐름·게이트만. |
| `DimensionHandler` (포트) | **Layer A 전용** 한 차원의 실행 계약 — `type() / needsRag() / needsBaseline() / handle(ctx)`. 실행 본체는 AnalysisEngine 위임. Layer B(IMPACT·ACTION)는 이 포트가 아니라 `PersonalizedHandler`(D69). |
| `DimensionHandlerRegistry` | `QueryType → DimensionHandler` 매핑. `@Component` 자동수집, 중복 타입 fail-fast, 구성 후 불변. |
| `SummaryHandler` | SUMMARY(Layer A) — 정본을 SUMMARY로 위임. 비교 아님 → baseline 미사용. 저장된 쉬운 요약(D67, `LawSummaries`)이 유효하면 그것으로 답하고 추론기를 부르지 않는다 — 없거나 낡았으면 실시간. |
| `DiffHandler` | DIFF(Layer A) — 정본+기준선을 DIFF로 위임. baseline null이면 제정(D42). |
| `PersonalizedHandler` | IMPACT·ACTION(Layer B, D69) — `AnalyzeRequest(IMPACT, law, baseline, profile, question)` 로 엔진 **1회**, 결과를 요청된 차원만 분할(IMPACT ← summary·claims·impacts / ACTION ← actions·effectiveInfo, 공통 uncertainties·disclaimer). 엔진이 낸 상황 질문·단계 설명은 ACTION 응답의 `Situation`(`questions`·`stepNotes`)에 싣는다(D71, IMPACT 응답은 `Situation.none()`). |
| `PersonalAdmission` (정적 함수 모음) | Layer B 판정의 **공통 부분**(D71) — 개정본인데 기준선 없음(`BASELINE_NOT_LOADED`)·하루 한도 차감(`DAILY_LIMIT`)·정본 없음·근거 부족 사유를 만든다. 첫 Layer B(디스패처)와 상황 재생성([[ActionRevisionUseCase]])이 **같은 함수를 공유**해 규칙이 갈라지지 않는다. |
| `Personalization` (sealed) | 개인화 판정 — `Usable(userId, UserProfile)` \| `Blocked(Reason)`. `anonymous()` = `Blocked(PROFILE_REQUIRED)`. 만드는 쪽은 `PersonalizationGate`([[ProfileUseCase]]). |
| `PersonalizationQuota` (포트) | `tryConsume(userId)` — 하루 한도 원자 차감, 초과면 false. 구현 `store.PersonalizationUsageStore`(V10). |
| `Unmet` (record) | 미충족 사유 `{ reason, message }` — `LAW_NOT_LOADED`·`BASELINE_NOT_LOADED`·`PROFILE_REQUIRED`·`CONSENT_OUTDATED`·`DAILY_LIMIT`·`INSUFFICIENT_GROUNDING`·`SITUATION_EXPIRED`(D71, 상황 재생성 전용 — 디스패처는 만들지 않는다)·`NOT_IMPLEMENTED`. 공개 API 코드와 같다([[AnalysisApi]]). |
| `DispatchContext` (record) | 핸들러 실행 재료 `{ law, baseline?, query }`. dispatcher가 정본 1회 조회해 조립. |
| `DispatchResult` (record) | 차원별 결과 묶음 `{ primaryType, filled, unmet, discovery? }`. `fullySatisfied()`. `discovery` 는 Discovery 질문일 때만(D70). |
| `DispatchResult.Discovery` (record) | LOOKUP 결과 `{ found: DiscoveryResult, profileBlocked: Unmet? }` — `profileBlocked` 는 프로필 토글이 켜졌는데 프로필을 못 쓴 경우의 사유(`PROFILE_REQUIRED`·`CONSENT_OUTDATED`). |

차원별 핸들러(component-specs §4#10):

| 핸들러 | 차원 | kind | 입력 | 상태 |
|---|---|---|---|---|
| `SummaryHandler` | SUMMARY | A | Law + `제개정이유` (저장 요약 우선, D67) | ✅ |
| `DiffHandler` | DIFF | A | Law(변경조문) + baseline + 개정문·부칙 | ✅ |
| `PersonalizedHandler` | IMPACT·ACTION | B | Law + baseline + UserProfile 속성 + 질문 | ✅ D69 |
| `LawDiscovery` | LOOKUP | 발견 | 질문 + (프로필 라벨) | ✅ D70 (레지스트리 밖) |

> **Layer B 를 별도 핸들러로 둔 이유(D69):** 두 차원을 Opus 1회로 함께 만들어야 하고(차원당 핸들러면 2회), 프로필·질문을 Layer A 핸들러 시그니처에 넣으면 구조적으로 그쪽에도 닿게 된다. 그래서 `DimensionHandler` 에는 프로필 인자가 없고(`needsProfile` 제거), 프로필·질문은 `PersonalizedHandler.handle` 인자로만 흐른다.

## Contract

`dispatch(AnalysisQuery query, String question, Personalization personalization, boolean profileSearch) → DispatchResult`

- 3인자 `dispatch(query, question, personalization)` 는 `profileSearch=false` 와 같다. `profileSearch` 는 요청의 "내 프로필 기준으로 찾기" 토글(D70)이며 Discovery 질문에서만 쓰인다.

- **전제**: `query`는 생성자 불변식을 통과한 해소·발견 완료 질의(미해소는 여기 오지 않음, fail-closed). `question` 은 사용자 원문(Layer B 프롬프트에만 쓰인다). `personalization` 은 유스케이스가 세션 userId 로 판정한 값(익명이면 `anonymous()`).
- **보장**: 분석 차원(`query.types()`)은 `filled`(성공) 또는 `unmet`(사유 포함) 중 정확히 하나에 담긴다. LOOKUP 결과는 `filled`·`unmet` 이 아니라 `discovery` 에만 담긴다(D70).
- **정본 조회**: `Target.Reference`면 `LawSource.find(lawId, efYd)`로 정본을, `findBaseline(lawId)`로 기준선을 **1회** 조회(모든 Layer A 핸들러가 공유 → 중복 fetch 제거).

## Business Flow

`Target.Discovery`(코퍼스 검색)는 LOOKUP 전용 경로 — `LawDiscovery` 1회 → `DispatchResult.discovery`(토글이 켜졌는데 `Personalization.Blocked` 면 `profileBlocked`), 한도 미차감(D70). 토글이 켜지고 `Usable` 이면 프로필 라벨만 검색어에 붙인다(`discover(question, Optional.of(profile))`) — 그 외에는 `Optional.empty()`. `Target.Reference`면 정본을 조회하고(미적재면 전 타입 `unmet`), `query.types`의 각 `type`에 대해:

1. `registry.get(type)` 없으면 → `unmet("핸들러 미구현")`. (IMPACT·ACTION 은 레지스트리를 거치지 않고 `PersonalizedHandler` 경로, D69 · LOOKUP 은 `LawDiscovery` 경로, D70)
2. IMPACT·ACTION(Layer B)은 이 순회가 아니라 아래 **Layer B(D69)** 항목의 순서(`Personalization.Blocked` 사유 → `BASELINE_NOT_LOADED` → `DAILY_LIMIT` → 핸들러 1회)로 처리한다. 프로필 유무 검사는 그 게이트의 몫이다.
3. `handler.needsBaseline() && baseline==null && !제정` → `unmet("기준선 미적재")`. **개정본인데 시행중본이 없는 데이터 이상을 조용히 '제정'으로 오인하지 않는다** — `제정`(baseline이 원래 없음)만 통과시킨다.
4. 그 외 → `filled(handler.handle(ctx))`.

> **`baseline == null`의 두 의미를 가른다:** ① `제정`(정상, 대조 대상 없음) ② 개정본인데 미적재(이상). `Law.amendKind() == 제정`으로 구별해 ①만 진행하고 ②는 `unmet`으로 거른다. 아니면 개정 법령이 "전부 신설"로 오분석된다(그라운딩 붕괴).

- **Layer B(D69):** IMPACT·ACTION 은 레지스트리를 거치지 않고 `PersonalizedHandler` 를 **한 번** 부른다. 순서: `Personalization.Blocked` → 사유 unmet · 개정본인데 기준선 없음 → `BASELINE_NOT_LOADED` · `PersonalizationQuota.tryConsume` 실패 → `DAILY_LIMIT` · 그 외 핸들러 1회 → 결과 분할. 프로필·질문은 이 호출 인자로만 — Layer A 핸들러에는 닿지 않는다.
- **공통 판정(D71):** 위 순서 중 기준선·한도 판정은 `PersonalAdmission` 공통 함수로 뽑아 두었다 — 같은 규칙을 **상황 재생성**([[ActionRevisionUseCase]], `POST /analyses/action-revisions`)이 그대로 재사용한다(개인화 → 만료 → 답 검증 → 정본·기준선 → 한도 → 엔진 ACTION). 한도는 첫 Layer B·재생성 모두 호출 직전 1회 차감([[cost-policy]]).
- **상황 질문(D71):** 첫 Layer B 의 ACTION 응답에는 `Situation`(엔진이 필터한 질문 0~3개 + 단계 설명)이 실린다. 디스패처·application 은 세션을 모르므로 질문을 결과에 싣기만 하고, 보관은 web 계층이 한다.
- **근거 부족(D69):** 어떤 핸들러든 `InsufficientGroundingException` 은 그 차원의 `INSUFFICIENT_GROUNDING` unmet 으로(다른 차원은 그대로).

## Invariants

- **fail-closed 승계**: 못 채우는 차원은 예외가 아니라 `unmet`으로 **정직하게** 표시(지어내지 않음).
- **레지스트리**: 한 `QueryType`당 핸들러 1개(중복은 구성 오류로 부팅 시 실패). 구성 후 불변.
- **정본 조회는 정확 조회**(벡터 아님, D56).

## Error Handling

- 예외를 던지지 않는다 — 라우팅 불가·정본 미적재·핸들러 부재·근거 부족(`InsufficientGroundingException`)은 전부 `unmet` 사유로 흡수(부분성공). 예외: Discovery 의 임베딩 API 예외는 `LawDiscovery` 에서 전파된다(D70).
- 핸들러(AnalysisEngine)가 재생성≤N 후에도 던지는 `InsufficientGroundingException` 은 그 차원의 `INSUFFICIENT_GROUNDING` unmet 으로 바뀌고, 다른 차원은 그대로 채워진다(D69).

## Side Effects

- `LawSource` **읽기** + Discovery 요청 시 임베딩 API 1회·색인 읽기(`LawDiscovery`, 한도·집계 없음, D70) + Layer B 요청 시 **한도 1회 차감**(`PersonalizationQuota` — 핸들러 호출 직전, 실패해도 환불 없음, D69). 그 외 쓰기·캐시·이벤트 없음. (답변 캐시는 측정 후 도입, D48·D51)

## 남은 경계 (의존 착지 후)

- **답변 캐싱(D51)** — 병목을 지표로 증명한 뒤(D48). context prompt-caching은 [[AnalysisEngine]] 관심사. Layer B 개인화 답은 저장하지 않는다(D69).
- **독립 Verification Gate(#12)** — [[AnalysisEngine]]가 재생성 루프 안에서 인용검증(`grounded`)을 수행하므로 지금은 중복. 필요가 생기면 조립 시점에 도입.

## 검증

- **Dispatcher 단위**(FakeHandler·Fake `LawSource`): 단일/복수 차원 filled · 미구현 차원 unmet · 정본 미적재 전타입 unmet · Discovery → `discovery` 결과(`LawDiscovery` 1회·한도 미차감, `filled`·`unmet` 비어 있음) · 토글+`Usable` 이면 프로필 전달, 토글+`Blocked` 면 `profileBlocked`(D70). `PersonalAdmission` 단위(기준선 없음·한도 초과·통과 — 디스패처·재생성 공용, D71) · Layer B(D69): 핸들러 1회 호출·쿼터 1회 소비로 두 차원 filled · `Blocked` 사유면 쿼터·핸들러 무접촉 · `DAILY_LIMIT` · 개정본인데 기준선 없음 → `BASELINE_NOT_LOADED` · 근거 부족 → `INSUFFICIENT_GROUNDING`.
- **핸들러 단위**: Personalized 의 ACTION 응답에 `Situation`(질문·단계 설명)이 실리고 IMPACT 응답은 `none()`(D71) · Summary/Diff가 올바른 `AnalyzeRequest(dimension, law, baseline)` 구성해 위임(CapturingEngine) · Summary 는 유효한 저장본이 있으면 엔진 무호출 · Personalized 는 두 차원 요청에도 엔진 1회·분할.
- **레지스트리 단위**: 타입 매핑 · 중복타입 실패.

## 의존 / 관련

[[QueryPlanner]](입력 `AnalysisQuery`) · [[AnalysisEngine]](실행 본체) · [[LawStore]](`LawSource` 구현) · [[LawDiscovery]](LOOKUP) · [[ActionRevisionUseCase]](`PersonalAdmission` 공유, D71) · [[component-specs]] §4 · [[v0.9-nl-query-planner]] §5.
