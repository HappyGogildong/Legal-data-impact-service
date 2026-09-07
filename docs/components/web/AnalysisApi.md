---
title: Analysis API — 온라인 오케스트레이터 + REST (spec-first)
status: Draft
version: 0.1
date: 2026-09-07
tags: [component, web, rest, orchestrator]
related: ["components/component-specs.md", "components/plan/QueryPlanner.md", "components/dispatch/QueryDispatcher.md", "mvp/service-api-spec.md"]
---

# Analysis API (Spring Web, 온라인 관통 경로)

> 자연어 질의를 받아 **계획→디스패치→그라운딩 답**으로 잇는 온라인 진입점. 계획([[QueryPlanner]])과 실행([[QueryDispatcher]])을 잇는 **글루 + HTTP 표면**이다 — 새 지능이 아니라 배선. 계약 SSOT: [[service-api-spec]] §3.0 · [[component-specs]] §4 #8(QueryDispatcher/Orchestrator).

## Responsibility

- **담당**: 온라인 요청 오케스트레이션(`plan()` → `dispatch()`), 4상태(미해소)·부분성공(`unmet`) 처리, `POST /api/v1/analyses` HTTP 계약(요청 파싱·응답 매핑·검증 400).
- **담당 안 함**: 번역·해소([[QueryPlanner]]) · 차원 라우팅·핸들러([[QueryDispatcher]]) · 프롬프트·LLM·인용검증([[AnalysisEngine]]) · 인증/세션 · 웹 UI(#14).

> **이번 증분 = Layer A Reference 관통.** `profilePresent=false` 고정(UserProfile Store #12 미구현) → Layer B(IMPACT·ACTION)는 dispatcher가 `unmet`으로 처리. Discovery/LOOKUP(#19)은 후속.

## Collaborators

- [[QueryPlanner]] (빈) — `plan(query, explicitRef, profilePresent) → PlanResult`.
- [[QueryDispatcher]] (@Component) — `dispatch(AnalysisQuery) → DispatchResult`.
- 외부 시스템: 없음(직접). LLM·DB는 위 둘 뒤로 격리.

## 구조 (컴포넌트) — `com.lia.core.web`

| 클래스 | 역할 |
|---|---|
| `AnalysisController` | `@RestController` — `POST /api/v1/analyses`. 요청 DTO 파싱·검증(빈 query→400) → `AnalysisService` → 응답 DTO 매핑. |
| `AnalysisService` | 오케스트레이터(웹 타입 비의존) — `plan()` 결과를 `switch`: `Unresolved` 그대로 / `Planned` → `dispatch()`. **`profilePresent=false` 고정.** |
| `AnalysisOutcome` (sealed) | `Analyzed(AnalysisQuery, DispatchResult)` \| `Unresolved(ResolutionResult)`. 컨트롤러가 두 경우를 빠짐없이 매핑. |
| `AnalyzeApiRequest` / `LawRefDto` | 요청 `{query, lawRef?, scope?}`. `LawRefDto`→`plan.LawRef` 변환. |
| `AnalyzeApiResponse` | 응답 — RESOLVED(answer·unmet) \| 미해소(resolution·message·candidates). |
| `ApiExceptionHandler` | `@RestControllerAdvice` — 검증 실패→400([[service-api-spec]] §4.1: **시스템 오류만 4xx**). |

## Contract

`AnalysisService.analyze(String query, LawRef explicitRef) → AnalysisOutcome`
- **전제**: `query` 비어있지 않음(컨트롤러가 선검증).
- **보장**: `plan()`이 `Unresolved`면 그대로 전달(fail-closed, 분석 안 함); `Planned`면 `dispatch()` 결과를 `Analyzed`로. 예외 없음.

## HTTP Contract ([[service-api-spec]] §3.0)

- `POST /api/v1/analyses` · 요청 `{ "query": <필수>, "lawRef"?: {lawId, effectiveDate, articleNo?}, "scope"?: [...] }`.
- **응답은 해소 4상태·분석 모두 HTTP 200** (4xx/5xx는 시스템 오류 전용, §4.1). 빈 `query` → **400**.
- **Analyzed** → `{ resolution:"RESOLVED", law_ref:"LAW:{lawId}@{efYd}", answer:{ <차원소문자>: ImpactResult }, unmet:[...], uncertainties, disclaimer }`.
- **Unresolved** → `{ resolution:<NOT_FOUND_YET|AMBIGUOUS|UNVERIFIED>, message, candidates? }`.
- `answer` 키는 채워진 차원(SUMMARY·DIFF) 소문자. `unmet`은 못 채운 차원(현재 Layer B·LOOKUP)·사유.

## Invariants
- **fail-closed 승계**: 미해소는 분석으로 새지 않는다(`plan()` 게이트가 이미 강제, 오케스트레이터는 통과만).
- **부분성공**: 일부 차원 실패는 전체 실패가 아니라 `unmet` 표기(§3.0).

## Error Handling
- 빈/누락 `query` → 400(`ApiExceptionHandler`). 그 외 해소·분석의 "실패"는 예외가 아니라 200 본문(resolution/unmet)으로 표현.
- `scope`·`law.title`은 이번 증분 미사용(§Out of scope).

## Side Effects
- 없음(순수 오케스트레이션) — 하위 컴포넌트가 정본 읽기·LLM 호출. 쓰기·캐시 없음(답변 캐시 D51은 후속).

## 검증
- `AnalysisServiceTest` — 실 `QueryPlanner`(FakeTranslator)+`QueryDispatcher`(FakeLawSource·FakeReasoner 핸들러)로 in-JVM 관통: 해소 NL→Analyzed(SUMMARY filled), 미해소→Unresolved.
- `AnalysisControllerTest`(`@WebMvcTest`+`@MockBean`) — 200 JSON 매핑·빈 query 400·미해소 200.
- 수동 라이브: 정본 선적재 + `LiaCoreApplication` 기동 → `curl POST /api/v1/analyses`.

## 의존 / 관련
[[QueryPlanner]] · [[QueryDispatcher]] · [[AnalysisEngine]] · [[service-api-spec]] §3.0 · [[component-specs]] §4 #8. 후속: UserProfile(#12)→Layer B · LawDiscovery(#19)→LOOKUP · 웹 UI(#14).
