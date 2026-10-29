---
title: AnalyzeUseCase — 온라인 분석 유스케이스 (application 계층)
status: Draft
version: 0.2
date: 2026-10-29
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

- **담당**: 개인화 판정 → plan → dispatch 조합, 4상태(미해소)·부분성공(`unmet`)을 결과 타입으로 표현, 자주 찾는 법령 집계(D66) 1회 기록.
- **담당 안 함**: HTTP·세션(web — `CurrentUser` 로 userId 만 꺼내 넘긴다) · 프로필 규칙([[ProfileUseCase|PersonalizationGate]]) · 번역·해소([[QueryPlanner]]) · 라우팅·핸들러·한도([[QueryDispatcher]]) · 프롬프트·LLM([[AnalysisEngine]]).

## 구조 (컴포넌트) — `com.lia.core.application.analysis`

| 클래스 | 역할 |
|---|---|
| `AnalyzeUseCase` | 유스케이스 — `analyze(query, explicitRef, userId?)`: `PersonalizationGate.check(userId)` → `plan(query, explicitRef, profilePresent)` → `switch`(Unresolved 그대로 / Planned → `dispatch(query, 질문 원문, personalization)` → 조회 집계). |
| `AnalysisOutcome` (sealed) | 결과 — `Analyzed(AnalysisQuery, DispatchResult, alternatives)` \| `Unresolved(ResolutionResult)`. `alternatives` = 같은 법령의 다른 시행예정본(D43, 응답의 "다른 시행일 보기"). HTTP 타입 비의존. |

협력자(생성자 주입): `QueryPlanner` · `QueryDispatcher` · `LawViews`(조회 집계 포트, D66) · `PersonalizationGate`(D69).

## 왜 `@Service`가 아니라 이 형태인가 (설계 결정)

- **네이밍**: 흔한 CRUD `XxxService`(컨트롤러↔리포지토리 트랜잭션 스크립트)와 혼동을 피하려 **`UseCase`**로 명명. 이건 리포지토리 글루가 아니라 pipeline 블록을 조합하는 유스케이스다.
- **프레임워크 비의존**: Spring 애노테이션을 **붙이지 않는다**. `@Service`/`@Component`는 코어에 `org.springframework` 의존을 심는데, application 코어는 자기가 Spring 위에서 도는지 몰라야 한다. 배선은 **`PipelineConfig`의 `@Bean`**(인프라 디테일)이 한다 — 추론/파이프라인 코어를 명시 `@Bean`으로 잇는 기존 방식과 일관. (`@Service`는 `@Component`의 의미 특수화일 뿐 추가 동작이 없어, 어차피 기능상 불필요.)

## Contract

`analyze(String query, LawRef explicitRef, Optional<UUID> userId) → AnalysisOutcome`
- **전제**: `query` 비어있지 않음(web가 선검증).
- **보장**: `plan()`이 `Unresolved`면 그대로(분석 안 함, fail-closed); `Planned`면 `dispatch()` 결과를 `Analyzed`로. 예외 없음(근거 부족도 차원별 `unmet`).
- **`explicitRef`**: 클라이언트가 이미 특정한 정본(브라우징 경로) — 있으면 [[QueryPlanner]]가 **법명 해소를 건너뛴다**. 없으면 자연어에서 해소.
- **`userId`**: 로그인 세션이 있을 때만(익명 허용). 게이트가 Layer B 가능 여부를 판정하고, 프롬프트에는 프로필 속성만 간다 — userId 는 한도 키로만 쓰인다(D41·D69).

## Invariants
- **fail-closed 승계**: 미해소는 분석으로 새지 않는다.
- **부분성공**: 일부 차원 실패는 전체 실패가 아니라 `unmet`(결과 타입에 담김).
- **조회 집계는 정본으로 답한 Reference 분석만**: 채운 차원이 하나도 없으면(정본 미적재 등) 세지 않는다.

## Side Effects
- 조회 집계 1회(`LawViews.record`, D66) — 실패는 삼키고 로그만(분석 응답을 바꾸지 않는다).
- 하위 블록이 정본 읽기·LLM 호출·개인화 한도 차감(디스패처).

## 검증
- `AnalyzeUseCaseTest`(in-JVM 관통) — 실 `QueryPlanner`(FakeTranslator)+`QueryDispatcher`(FakeLawSource·FakeReasoner)로: 해소 NL→Analyzed(SUMMARY) · **explicitRef 주면 해소 생략** · 미해소→Unresolved · 로그인 사용자의 게이트 판정이 디스패처까지(옛 동의 → `CONSENT_OUTDATED`) · 조회 집계(정본 답만 · 기록 실패해도 결과 그대로).

## 의존 / 관련
[[QueryPlanner]] · [[QueryDispatcher]] · [[ProfileUseCase]](`PersonalizationGate`) · [[AnalysisApi]](web 어댑터·응답 매핑) · [[TrendingApi]](조회 집계) · [[component-specs]] §4 #8. 후속: LawDiscovery(#19)→LOOKUP.
