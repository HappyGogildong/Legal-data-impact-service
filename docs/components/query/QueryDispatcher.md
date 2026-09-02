---
title: Query Dispatcher — 컴포넌트 설계 · 구현 착수
status: Draft
version: 0.1
date: 2026-09-03
tags: [component, pipeline, dispatcher, orchestration]
related: ["components/component-specs.md", "components/query/QueryPlanner.md", "components/analyze/AnalysisEngine.md", "architecture/v0.9-nl-query-planner.md"]
---

# Query Dispatcher (Spring, 차원 라우팅·조립)

> 검증된 `AnalysisQuery`를 **차원(QueryType)별 핸들러로 라우팅**하고 결과를 조립한다. 계획(`QueryPlanner`)과 실행(`AnalysisEngine`)을 잇는 오케스트레이터. 옛 `AnalysisPipeline`+`CommandRegistry`→`QueryDispatcher`, `AnalysisCommand`→`DimensionHandler`(D47). 관련: [[QueryPlanner]] · [[AnalysisEngine]] · [[component-specs]] §4 #8~#10 · [[v0.9-nl-query-planner]] §5

## 1. 역할·경계

- **입력:** `AnalysisQuery` (해소·발견 완료된 타입 DTO — `QueryPlanner`가 산출).
- **출력:** `DispatchResult` — 차원별 결과 묶음(부분성공).
- **하는 일:** target 해석(정본 조회) → `types` 순회 라우팅 → 핸들러 실행 → 취합.
- **안 하는 일:** 프롬프트 빌드·LLM 호출·인용검증은 **핸들러가 위임한 [[AnalysisEngine]]**가 한다. dispatcher는 조립·라우팅만.

> **차원은 사용자 선택 모드가 아니다(D46).** 플래너가 질의에서 고른 내부 분해 + 그라운딩 가드레일. 한 질의가 여러 차원을 요구할 수 있어(포괄질문) `types`는 집합이고, 결과는 부분성공으로 조립된다.

## 2. 데이터 흐름

```
QueryPlanner → PlanResult.Planned(AnalysisQuery)
                       │
                       ▼
              ┌─────────────────┐
              │ QueryDispatcher │  ① Reference → LawStore.find + findBaseline (1회)
              │                 │  ② query.types 순회 → 레지스트리 조회 → 게이트
              └───────┬─────────┘  ③ DispatchResult (filled + unmet)
                      │
        ┌─────────────┼──────────────┐
        ▼             ▼              ▼
  SummaryHandler  DiffHandler   (Impact/Action/Lookup — 후속)
    │ Layer A        Layer A         │ 레지스트리에 없음
    └──→ AnalysisEngine.analyze()    └──→ dispatcher가 unmet 처리
```

**dispatcher가 정본(Law·baseline)을 1회 조회**해 `DispatchContext`로 핸들러에 전달한다 — 모든 Layer A 핸들러가 같은 정본을 쓰므로 중복 fetch를 제거하고, 핸들러는 저장소를 몰라도 된다(조립·차원 지정에만 집중).

## 3. 타입 계약

### `DimensionHandler` (포트, §4#10 형태)

```java
interface DimensionHandler {
    QueryType type();          // 이 핸들러가 담당하는 차원
    boolean needsProfile();    // Layer B 게이트용 (Summary/Diff=false)
    boolean needsRag();        // Summary/Diff=false
    AnalyzeResponse handle(DispatchContext ctx);  // 실행 본체 = AnalysisEngine 위임
}
```

### `DimensionHandlerRegistry` (§4#9)

`@Component` 자동 발견 → `QueryType → DimensionHandler` 매핑. **중복 타입은 구성 오류로 즉시 실패**(같은 차원 핸들러 2개 = 배선 버그).

### `DispatchContext`

해소된 실행 재료 — `{ Law law, Law baseline, AnalysisQuery query }`. `baseline`은 제정이면 null(정상, D42).

### `DispatchResult` (부분성공 모델)

```java
record DispatchResult(
    QueryType primaryType,
    Map<QueryType, AnalyzeResponse> filled,   // 채워진 차원
    Map<QueryType, String> unmet              // 못 채운 차원 + 사유
) { boolean fullySatisfied(); }
```

D46의 "주 타입 1 + 집합"·부분성공(`unmet`)을 구조화. FE는 `filled`를 렌더하고 `unmet`은 안내 문구로.

## 4. Dispatcher 알고리즘

`Target.Reference`면 `LawStore.find(lawId, efYd)`로 정본을, `findBaseline(lawId)`로 기준선을 1회 조회한다. 정본이 없으면 **전 타입 unmet**(`"정본 미적재: {lawId}@{efYd}"`). 이후 `query.types`의 각 `type`에 대해:

1. `handler = registry.get(type)` — **없으면** `unmet("핸들러 미구현: " + type)`. → 현재 LOOKUP·IMPACT·ACTION이 여기 걸린다.
2. `handler.needsProfile() && !query.profileBound()` → `unmet("프로필 필요")`. → 미래 Layer B가 프로필 없이 오는 경우.
3. 그 외 → `filled(handler.handle(ctx))`.

`Target.Discovery`(코퍼스 검색)는 LOOKUP 전용 경로다. `LookupHandler`가 아직 없으므로 현재 Discovery 질의는 전부 unmet — LawDiscovery(#19) 착지 후 연결한다.

## 5. 핸들러 5종 (§4#10)

| 핸들러 | kind | 입력 | 출력 핵심 | 상태 |
|---|---|---|---|---|
| `SummaryHandler` | A | Law + `제개정이유` | summary, claims | ✅ 이번 |
| `DiffHandler` | A | Law(변경조문) + baseline + 개정문·부칙 | claims(현행→개정), 시행일 | ✅ 이번 |
| `LookupHandler` | 발견 | DiscoveryCriteria + (프로필) | 후보 법령 랭킹 | ⬜ LawDiscovery(#19) 후 |
| `ImpactHandler` | B | Law(변경조문) + LawFacts + **UserProfile** | impacts | ⬜ UserProfile·LawFacts·Layer B 후 |
| `ActionHandler` | B | Law + 부칙(시행일) + **UserProfile** | actions(deadline, basis) | ⬜ 상동 |

이번 증분의 두 Layer A 핸들러는 얇은 어댑터다 — `DispatchContext`에서 `AnalyzeRequest(type, law, baseline)`를 구성해 `AnalysisEngine.analyze()`에 위임하고 `AnalyzeResponse`를 그대로 돌려준다.

## 6. 이번 증분에서 뺀 것 (의존 착지 후)

- **ImpactHandler·ActionHandler** — UserProfile(#12)·LawFacts·AnalysisEngine Layer B 필요.
- **LookupHandler** — LawDiscovery(#19) 필요.
- **답변 캐싱(D51)** — 측정 선행 철학(D48). 답 캐시는 병목을 지표로 증명한 뒤. context prompt-caching은 [[AnalysisEngine]] 내부 관심사.
- **독립 Verification Gate(#12)** — [[AnalysisEngine]]가 이미 1차 인용검증(FaithfulnessGate)을 수행하므로 Layer A만 있는 지금은 중복. 최종 게이트는 Layer B·조립 시점으로 이연.

## 7. 불변식·정합성

- **fail-closed 승계:** dispatcher는 `AnalysisQuery`(생성자 불변식 통과)만 받는다 — 미해소가 분석으로 새지 않는다. 못 채우는 차원은 예외가 아니라 **`unmet`으로 정직하게 표시**(부분성공, 지어내지 않음).
- **정본 조회는 정확 조회다** — 벡터 검색 아님. 벡터(ChunkStore)는 Discovery 전용(D56).
- **레지스트리 중복 타입 = 구성 오류**로 부팅 시 실패.

## 8. 테스트 (TDD)

- **Dispatcher 단위** (FakeHandler + 실 `Law` 픽스처, `LawStore`는 Fake/스텁):
  - 단일 차원 Reference → filled 1건
  - 복수 차원(SUMMARY+DIFF) → filled 2건
  - 미구현 차원(IMPACT) → unmet("핸들러 미구현")
  - `needsProfile` fake 핸들러 + `profileBound=false` → unmet("프로필 필요")
  - 정본 미적재 → 전 타입 unmet
  - `Target.Discovery` → unmet
- **핸들러 단위**: `SummaryHandler`/`DiffHandler`가 올바른 `AnalyzeRequest(dimension, law, baseline)`를 구성해 AnalysisEngine(Fake Reasoner)로 위임하는지.
- **레지스트리 단위**: `@Component` 수집 → 타입 매핑, 중복 타입 검출.
