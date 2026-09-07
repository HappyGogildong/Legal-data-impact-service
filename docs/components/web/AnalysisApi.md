---
title: Analysis API — REST 어댑터 (web 계층)
status: Draft
version: 0.2
date: 2026-09-08
tags: [component, web, rest, adapter]
related: ["components/component-specs.md", "components/application/AnalyzeUseCase.md", "mvp/service-api-spec.md"]
---

# Analysis API (web 계층, HTTP 어댑터)

> `POST /api/v1/analyses`의 **HTTP 인바운드 어댑터**. 요청 파싱·검증·상태코드와 응답 매핑만 담당하고, 유스케이스 조합은 [[AnalyzeUseCase]](application)에 위임한다. 계약 SSOT: [[service-api-spec]] §3.0.

## 계층·경계

```
web (이 문서)         →  application            →  pipeline · domain · store
Controller·Mapper·DTO    AnalyzeUseCase             QueryPlanner · QueryDispatcher ...
```

- **담당**: HTTP 요청/응답 경계 — 요청 DTO 파싱·검증(빈 query→400), [[AnalyzeUseCase]] 위임, 결과→JSON 매핑, 상태코드.
- **담당 안 함**: plan→dispatch 조합·4상태 판정([[AnalyzeUseCase]]) · 번역·해소·라우팅(pipeline).

## 구조 (컴포넌트) — `com.lia.core.web`

| 클래스 | 역할 |
|---|---|
| `AnalysisController` | `@RestController` — **HTTP 경계만**: `POST /api/v1/analyses` 검증(빈 query→400) → `AnalyzeUseCase` 위임 → `AnalysisResponseMapper`로 본문 매핑 후 200. |
| `AnalysisResponseMapper` | **표현 매핑** `@Component` — `AnalysisOutcome`→스펙 응답 `Map`(snake_case `law_ref`·차원 소문자 `answer` 키·`unmet`·candidates). 컨트롤러에서 분리해 독립 단위 테스트. |
| `AnalyzeApiRequest` / `LawRefDto` | 요청 `{query, lawRef?, scope?}`. `LawRefDto`→`plan.LawRef` 변환(`explicitRef()`). |
| `ApiExceptionHandler` | `@RestControllerAdvice` — 검증 실패→400([[service-api-spec]] §4.1: **시스템 오류만 4xx**). |

> **응답을 typed DTO가 아니라 `Map`으로 두는 이유**: 응답이 희소·다형적(RESOLVED vs 미해소)이고 가장 복잡한 `answer`가 동적 차원 키 Map이라 DTO 이득이 적고, 소비자(웹 #14)가 아직 없다(YAGNI). typed `AnalyzeApiResponse`는 #14가 계약을 소비할 때 도입한다(snake_case도 그때 정식 처리).

## HTTP Contract ([[service-api-spec]] §3.0)

- `POST /api/v1/analyses` · 요청 `{ "query": <필수>, "lawRef"?: {lawId, effectiveDate, articleNo?}, "scope"?: [...] }`.
- **응답은 해소 4상태·분석 모두 HTTP 200** (4xx/5xx는 시스템 오류 전용, §4.1). 빈 `query` → **400**.
- **Analyzed** → `{ resolution:"RESOLVED", law_ref:"LAW:{lawId}@{efYd}", answer:{ <차원소문자>: ImpactResult }, unmet:[...], uncertainties, disclaimer }`.
- **Unresolved** → `{ resolution:<NOT_FOUND_YET|AMBIGUOUS|UNVERIFIED>, message, candidates? }`.

## Error Handling
- 빈/누락 `query` → 400(`ApiExceptionHandler`). 해소 실패·근거 부족은 예외가 아니라 200 본문(resolution/unmet).
- `scope`·`law.title`은 이번 증분 미사용(후속).

## Side Effects
- 없음 — 하위 계층이 정본 읽기·LLM 호출.

## 검증 (단위 5)
- `AnalysisResponseMapperTest`(2) — 매핑 직접: Analyzed(law_ref·answer 키·unmet·disclaimer) · Unresolved(resolution·message·candidates).
- `AnalysisControllerTest`(3, 순수 단위·`AnalyzeUseCase` 목·실 매퍼) — 위임·상태(200)·빈 query 예외. **Boot 4.0 `test-autoconfigure`에 `@WebMvcTest` 미제공**이라 슬라이스 대신 순수 단위로. HTTP 라우팅/직렬화는 수동 curl.
- 수동 라이브: 정본 선적재 + `LiaCoreApplication` 기동 → `curl POST /api/v1/analyses`.

## 의존 / 관련
[[AnalyzeUseCase]](application·유스케이스) · [[service-api-spec]] §3.0 · [[component-specs]] §4 #8. 후속: 웹 UI(#14, typed DTO 도입 시점).
