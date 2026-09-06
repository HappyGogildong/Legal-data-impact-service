---
title: Query Dispatcher — 컴포넌트 설계 · 구현 착수
status: Draft
version: 0.2
date: 2026-09-03
tags: [component, pipeline, dispatcher, orchestration]
related: ["components/component-specs.md", "components/plan/QueryPlanner.md", "components/analyze/AnalysisEngine.md", "components/store/LawStore.md", "architecture/v0.9-nl-query-planner.md"]
---

# QueryDispatcher (Spring, 차원 라우팅·조립)

> 검증된 `AnalysisQuery`를 **차원(QueryType)별 핸들러로 라우팅**하고 결과를 조립한다. 계획(`QueryPlanner`)과 실행(`AnalysisEngine`)을 잇는 오케스트레이터(D47). 관련: [[QueryPlanner]] · [[AnalysisEngine]] · [[component-specs]] §4 #8~#10 · [[v0.9-nl-query-planner]] §5

## Responsibility

- **담당**: target 해석(정본 정확조회) · `types` 순회 라우팅 · 부분성공 조립(`DispatchResult`).
- **담당 안 함**: 프롬프트 빌드·LLM 호출·인용검증(→ 핸들러가 위임한 [[AnalysisEngine]]) · 자연어 번역·해소(→ [[QueryPlanner]]) · 벡터 검색(→ Discovery 전용, D56).

> **차원은 사용자 선택 모드가 아니다(D46).** 플래너가 질의에서 고른 내부 분해 + 그라운딩 가드레일. 한 질의가 여러 차원을 요구할 수 있어(포괄질문) `types`는 집합이고, 결과는 **부분성공**으로 조립된다.

## Collaborators

- **포트**: `LawSource`(정본·기준선 정확조회, [[LawStore]] 구현) · `DimensionHandlerRegistry`(차원→핸들러).
- **간접**: `DimensionHandler` 구현체들이 [[AnalysisEngine]]에 추론을 위임.
- **외부 시스템**: 없음(직접). 정본 읽기는 `LawSource` 뒤로 격리.

## 구조 (컴포넌트)

| 클래스 | 역할 |
|---|---|
| `QueryDispatcher` | 오케스트레이터 — target 해석 → 차원 라우팅 → `DispatchResult` 조립. 흐름·게이트만. |
| `DimensionHandler` (포트) | 한 차원의 실행 계약 — `type() / needsProfile() / needsRag() / needsBaseline() / handle(ctx)`. 실행 본체는 AnalysisEngine 위임. |
| `DimensionHandlerRegistry` | `QueryType → DimensionHandler` 매핑. `@Component` 자동수집, 중복 타입 fail-fast, 구성 후 불변. |
| `SummaryHandler` | SUMMARY(Layer A) — 정본을 SUMMARY로 위임. 비교 아님 → baseline 미사용. |
| `DiffHandler` | DIFF(Layer A) — 정본+기준선을 DIFF로 위임. baseline null이면 제정(D42). |
| `DispatchContext` (record) | 핸들러 실행 재료 `{ law, baseline?, query }`. dispatcher가 정본 1회 조회해 조립. |
| `DispatchResult` (record) | 차원별 결과 묶음 `{ primaryType, filled, unmet }`. `fullySatisfied()`. |

핸들러 5종 전체 계획(§4#10):

| 핸들러 | kind | 입력 | 출력 핵심 | 상태 |
|---|---|---|---|---|
| `SummaryHandler` | A | Law + `제개정이유` | summary, claims | ✅ |
| `DiffHandler` | A | Law(변경조문) + baseline + 개정문·부칙 | claims(현행→개정), 시행일 | ✅ |
| `LookupHandler` | 발견 | DiscoveryCriteria + (프로필) | 후보 법령 랭킹 | ⬜ LawDiscovery(#19) 후 |
| `ImpactHandler` | B | Law(변경조문) + LawFacts + **UserProfile** | impacts | ⬜ UserProfile·LawFacts·Layer B 후 |
| `ActionHandler` | B | Law + 부칙(시행일) + **UserProfile** | actions(deadline, basis) | ⬜ 상동 |

## Contract

`dispatch(AnalysisQuery query) → DispatchResult`

- **전제**: `query`는 생성자 불변식을 통과한 해소·발견 완료 질의(미해소는 여기 오지 않음, fail-closed).
- **보장**: 요청한 모든 차원(`query.types()`)이 `filled`(성공) 또는 `unmet`(사유 포함) 중 정확히 하나에 담긴다.
- **정본 조회**: `Target.Reference`면 `LawSource.find(lawId, efYd)`로 정본을, `findBaseline(lawId)`로 기준선을 **1회** 조회(모든 Layer A 핸들러가 공유 → 중복 fetch 제거).

## Business Flow

`Target.Discovery`(코퍼스 검색)는 LOOKUP 전용 경로 — LookupHandler·LawDiscovery(#19) 착지 전까지 전 타입 `unmet`. `Target.Reference`면 정본을 조회하고(미적재면 전 타입 `unmet`), `query.types`의 각 `type`에 대해:

1. `registry.get(type)` 없으면 → `unmet("핸들러 미구현")`. (현재 LOOKUP·IMPACT·ACTION)
2. `handler.needsProfile() && !query.profileBound()` → `unmet("프로필 필요")`. (미래 Layer B가 프로필 없이 올 때)
3. `handler.needsBaseline() && baseline==null && !제정` → `unmet("기준선 미적재")`. **개정본인데 시행중본이 없는 데이터 이상을 조용히 '제정'으로 오인하지 않는다** — `제정`(baseline이 원래 없음)만 통과시킨다.
4. 그 외 → `filled(handler.handle(ctx))`.

> **`baseline == null`의 두 의미를 가른다:** ① `제정`(정상, 대조 대상 없음) ② 개정본인데 미적재(이상). `Law.amendKind() == 제정`으로 구별해 ①만 진행하고 ②는 `unmet`으로 거른다. 아니면 개정 법령이 "전부 신설"로 오분석된다(그라운딩 붕괴).

## Invariants

- **fail-closed 승계**: 못 채우는 차원은 예외가 아니라 `unmet`으로 **정직하게** 표시(지어내지 않음).
- **레지스트리**: 한 `QueryType`당 핸들러 1개(중복은 구성 오류로 부팅 시 실패). 구성 후 불변.
- **정본 조회는 정확 조회**(벡터 아님, D56).

## Error Handling

- 예외를 던지지 않는다 — 라우팅 불가·정본 미적재·핸들러 부재는 전부 `unmet` 사유로 흡수(부분성공).
- 핸들러 내부(AnalysisEngine)의 `InsufficientGroundingException` 전파 정책은 [[AnalysisEngine]] 소관. (현재는 핸들러가 그대로 전파 — 재생성≤N 후 근거부족)

## Side Effects

- 없음(순수 조립) — `LawSource` **읽기**만. 쓰기·캐시·이벤트 없음. (답변 캐시는 측정 후 도입, D48·D51)

## 이번 증분 경계 (의존 착지 후)

- **ImpactHandler·ActionHandler** — UserProfile(#12)·LawFacts·AnalysisEngine Layer B 필요.
- **LookupHandler** — LawDiscovery(#19) 필요.
- **답변 캐싱(D51)** — 병목을 지표로 증명한 뒤(D48). context prompt-caching은 [[AnalysisEngine]] 관심사.
- **독립 Verification Gate(#12)** — [[AnalysisEngine]]가 1차 인용검증(FaithfulnessGate)을 수행하므로 Layer A만 있는 지금은 중복. 최종 게이트는 Layer B·조립 시점으로 이연.

## 검증

- **Dispatcher 단위**(FakeHandler·Fake `LawSource`): 단일/복수 차원 filled · 미구현 차원 unmet · `needsProfile`+`profileBound=false` unmet · 정본 미적재 전타입 unmet · Discovery unmet.
- **핸들러 단위**: Summary/Diff가 올바른 `AnalyzeRequest(dimension, law, baseline)` 구성해 위임(CapturingEngine).
- **레지스트리 단위**: 타입 매핑 · 중복타입 실패.

## 의존 / 관련

[[QueryPlanner]](입력 `AnalysisQuery`) · [[AnalysisEngine]](실행 본체) · [[LawStore]](`LawSource` 구현) · [[component-specs]] §4 · [[v0.9-nl-query-planner]] §5.
