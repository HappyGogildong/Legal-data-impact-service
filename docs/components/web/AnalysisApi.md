---
title: Analysis API — REST 어댑터 (web 계층)
status: Draft
version: 0.3
date: 2026-10-24
tags: [component, web, rest, adapter]
related: ["components/component-specs.md", "components/application/AnalyzeUseCase.md", "mvp/service-api-spec.md", "frontend/frontend-stack.md"]
---

# Analysis API (web 계층, HTTP 어댑터)

> `POST /api/v1/analyses`의 **HTTP 인바운드 어댑터**. 요청 파싱·검증·상태코드와 응답 매핑만 담당하고, 유스케이스 조합은 [[AnalyzeUseCase]](application)에 위임한다. 계약 SSOT: [[service-api-spec]] §3.0.

## 계층·경계

```
web (이 문서)         →  application            →  pipeline · domain · store
Controller·Mapper·DTO    AnalyzeUseCase             QueryPlanner · QueryDispatcher ...
```

- **담당**: HTTP 요청/응답 경계 — 요청 DTO 파싱·검증(빈 query→400), [[AnalyzeUseCase]] 위임, 결과→typed 응답 매핑(법령 헤더·근거 원문 조회 포함), 상태코드.
- **담당 안 함**: plan→dispatch 조합·4상태 판정([[AnalyzeUseCase]]) · 번역·해소·라우팅(pipeline).

## 구조 (컴포넌트) — `com.lia.core.web`

| 클래스 | 역할 |
|---|---|
| `AnalysisController` | `@RestController` — **HTTP 경계만**: `POST /api/v1/analyses` 검증(빈 query→400) → `AnalyzeUseCase` 위임 → `AnalysisResponseMapper`로 응답 매핑 후 200. |
| `AnalysisResponseMapper` | **표현 매핑** — `AnalysisOutcome` → `AnalysisApiResponse`. 법령 헤더(`law`)는 `LawSource`에서, 다른 시행일(`alternatives`)은 유스케이스 결과에서, 근거(`sources`)는 `CitedSourceResolver`로. `answer`·`unmet` 키는 차원 소문자, 순서는 `QueryType` 선언 순. `unmet` 값은 디스패처의 `Unmet`(사유 코드·설명) 그대로. |
| `AnalysisApiResponse` | **typed 응답 DTO**(record). 값이 없는 최상위 필드는 생략(`@JsonInclude(NON_NULL)`). 중첩 `LawHeader`·`LawBrief`·`CitedSource`. |
| `CitedSourceResolver` | 인용키 → 근거 라벨·원문. 형식 `LAW:{lawId}[@{efYd}][:art:{no} \| :addenda:{no} \| :amend][#{n}]` — 시행일이 없으면 시행중 기준선(`findBaseline`). 조문은 "법령명 제N조(제목)"·본문·변경 유형 + 시행예정본의 개정·삭제·이동 조문이면 **현행 원문**(`previousText` — 기준선의 같은 번호, 이동은 옛 번호), 부칙은 "법령명 부칙 {no}", `:amend`는 "개정문", 접미사가 없으면 "개정 이유". 형식이 다르거나 찾지 못하면 `label=sourceId`, `text=null`. 같은 법령 조회는 요청 안에서 한 번만. |
| `AnalyzeApiRequest` / `LawRefDto` | 요청 `{query, lawRef?, scope?}`. `LawRefDto`→`plan.LawRef` 변환(`explicitRef()`). |
| `ApiExceptionHandler` | `@RestControllerAdvice` — 검증 실패→400([[service-api-spec]] §4.1: **시스템 오류만 4xx**). |

> **왜 웹 매퍼가 `LawSource`를 읽나**: 법령명·근거 원문은 화면 표시용 참조 데이터라 표현 매핑의 일부로 둔다. 디스패처도 같은 정본을 읽으므로 요청당 조회가 1~2회 늘어난다 — 응답 지연에 비해 작다(ponytail: 문제가 되면 디스패치 결과에 정본을 실어 재사용).

## HTTP Contract ([[service-api-spec]] §3.0)

- `POST /api/v1/analyses` · 요청 `{ "query": <필수>, "lawRef"?: {lawId, effectiveDate, articleNo?}, "scope"?: [...] }`.
- **응답은 해소 4상태·분석 모두 HTTP 200** (4xx/5xx는 시스템 오류 전용, §4.1). 빈 `query` → **400**.
- **Analyzed** → `{ resolution:"RESOLVED", law:{ref,lawId,title,effectiveDate,lawType,ministry}, alternatives:[LawBrief], answer:{ <차원소문자>: ImpactResult }, unmet:{ <차원소문자>: {reason, message} }, sources:[CitedSource], disclaimer }`. `reason` ∈ `LAW_NOT_LOADED`·`BASELINE_NOT_LOADED`·`PROFILE_REQUIRED`·`NOT_IMPLEMENTED`.
- **Unresolved** → `{ resolution:<NOT_FOUND_YET|AMBIGUOUS|UNVERIFIED>, message, candidates? }`.
- **OpenAPI** — springdoc이 `/v3/api-docs`(경로 `/api/**`만)를 만든다. `OpenApiSnapshotTest`가 레포 `api/openapi.json`과 비교해 계약 변경을 드러낸다. 운영은 `SPRINGDOC_API_DOCS_ENABLED=false`.

## Error Handling
- 빈/누락 `query` → 400(`ApiExceptionHandler`). 해소 실패·근거 부족은 예외가 아니라 200 본문(resolution/unmet).
- `scope`는 이번 증분 미사용(후속).

## Side Effects
- 정본 읽기(`LawSource`) — 법령 헤더·근거 원문. 쓰기 없음.

## 검증
- `AnalysisResponseMapperTest` — Analyzed(법령 헤더·정본 미적재 헤더·alternatives·answer 순서·unmet·sources·disclaimer) · Unresolved(candidates) · JSON 직렬화(camelCase·null 생략).
- `CitedSourceResolverTest` — 조문(현행 원문 포함)·부칙·개정문·개정 이유·기준선(시행일 없음)·하위 청크(`#n`)·형식 불일치·조문 없음·중복 제거.
- `AnalysisControllerTest` — 위임·상태(200)·빈 query 예외.
- `OpenApiSnapshotTest` — `/v3/api-docs` = `api/openapi.json`.

## 의존 / 관련
[[AnalyzeUseCase]](application·유스케이스) · [[service-api-spec]] §3.0 · [[component-specs]] §4 #8 · [[frontend-stack]](웹 TS 타입 생성).
