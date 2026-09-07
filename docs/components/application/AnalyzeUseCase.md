---
title: AnalyzeUseCase — 온라인 분석 유스케이스 (application 계층)
status: Draft
version: 0.1
date: 2026-09-08
tags: [component, application, usecase, orchestration]
related: ["components/component-specs.md", "components/plan/QueryPlanner.md", "components/dispatch/QueryDispatcher.md", "components/web/AnalysisApi.md"]
---

# AnalyzeUseCase (application 계층, 온라인 유스케이스)

> 계획([[QueryPlanner]])과 실행([[QueryDispatcher]])을 **조합해** "자연어 질의 → 그라운딩 답"을 완성하는 유스케이스. web(HTTP)과 pipeline(블록) 사이의 **application 계층**이다. 관련: [[AnalysisApi]](web 어댑터) · [[component-specs]] §4 #8.

## 계층·경계

```
web (HTTP 어댑터)  →  application (유스케이스)  →  pipeline(블록) · domain · store
AnalysisController     AnalyzeUseCase              QueryPlanner · QueryDispatcher · AnalysisEngine
```

- **담당**: plan→dispatch 조합, 4상태(미해소)·부분성공(`unmet`)을 결과 타입으로 표현.
- **담당 안 함**: HTTP·직렬화([[AnalysisApi]] web) · 번역·해소([[QueryPlanner]]) · 라우팅·핸들러([[QueryDispatcher]]) · 프롬프트·LLM([[AnalysisEngine]]).

## 구조 (컴포넌트) — `com.lia.core.application.analysis`

| 클래스 | 역할 |
|---|---|
| `AnalyzeUseCase` | 유스케이스 — `analyze(query, explicitRef)`: `plan()` → `switch`(Unresolved 그대로 / Planned → `dispatch()`). **`profilePresent=false` 고정**(#12 미도입 → Layer A). |
| `AnalysisOutcome` (sealed) | 결과 — `Analyzed(AnalysisQuery, DispatchResult)` \| `Unresolved(ResolutionResult)`. HTTP 타입 비의존. |

## 왜 `@Service`가 아니라 이 형태인가 (설계 결정)

- **네이밍**: 흔한 CRUD `XxxService`(컨트롤러↔리포지토리 트랜잭션 스크립트)와 혼동을 피하려 **`UseCase`**로 명명. 이건 리포지토리 글루가 아니라 pipeline 블록을 조합하는 유스케이스다.
- **프레임워크 비의존**: Spring 애노테이션을 **붙이지 않는다**. `@Service`/`@Component`는 코어에 `org.springframework` 의존을 심는데, application 코어는 자기가 Spring 위에서 도는지 몰라야 한다. 배선은 **`PipelineConfig`의 `@Bean`**(인프라 디테일)이 한다 — 추론/파이프라인 코어를 명시 `@Bean`으로 잇는 기존 방식과 일관. (`@Service`는 `@Component`의 의미 특수화일 뿐 추가 동작이 없어, 어차피 기능상 불필요.)

## Contract

`analyze(String query, LawRef explicitRef) → AnalysisOutcome`
- **전제**: `query` 비어있지 않음(web가 선검증).
- **보장**: `plan()`이 `Unresolved`면 그대로(분석 안 함, fail-closed); `Planned`면 `dispatch()` 결과를 `Analyzed`로. 예외 없음.
- **`explicitRef`**: 클라이언트가 이미 특정한 정본(브라우징 경로) — 있으면 [[QueryPlanner]]가 **법명 해소를 건너뛴다**. 없으면 자연어에서 해소.

## Invariants
- **fail-closed 승계**: 미해소는 분석으로 새지 않는다.
- **부분성공**: 일부 차원 실패는 전체 실패가 아니라 `unmet`(결과 타입에 담김).

## Side Effects
- 없음(순수 조합) — 하위 블록이 정본 읽기·LLM 호출.

## 검증
- `AnalyzeUseCaseTest`(3, in-JVM 관통) — 실 `QueryPlanner`(FakeTranslator)+`QueryDispatcher`(FakeLawSource·FakeReasoner)로: 해소 NL→Analyzed(SUMMARY) · **explicitRef 주면 해소 생략** · 미해소→Unresolved.

## 의존 / 관련
[[QueryPlanner]] · [[QueryDispatcher]] · [[AnalysisApi]](web 어댑터·응답 매핑) · [[component-specs]] §4 #8. 후속: UserProfile(#12)→Layer B · LawDiscovery(#19)→LOOKUP.
