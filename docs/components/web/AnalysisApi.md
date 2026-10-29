---
title: Analysis API — REST 어댑터 (web 계층)
status: Draft
version: 0.5
date: 2026-10-29
tags: [component, web, rest, adapter]
related: ["components/component-specs.md", "components/application/AnalyzeUseCase.md", "components/application/ActionRevisionUseCase.md", "mvp/service-api-spec.md", "frontend/frontend-stack.md"]
---

# Analysis API (web 계층, HTTP 어댑터)

> `POST /api/v1/analyses`·`POST /api/v1/analyses/action-revisions`(D71)의 **HTTP 인바운드 어댑터**. 요청 파싱·검증·상태코드와 응답 매핑만 담당하고, 유스케이스 조합은 [[AnalyzeUseCase]](application)에 위임한다. 계약 SSOT: [[service-api-spec]] §3.0.

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
| `AnalysisResponseMapper` | **표현 매핑** — `AnalysisOutcome` → `AnalysisApiResponse`(ACTION 이 채워지고 질문이 있을 때 `situation`, D71). 법령 헤더(`law`)는 `LawSource`에서, 다른 시행일(`alternatives`)은 유스케이스 결과에서, 근거(`sources`)는 `CitedSourceResolver`로. `answer`·`unmet` 키는 차원 소문자, 순서는 `QueryType` 선언 순. `unmet` 값은 디스패처의 `Unmet`(사유 코드·설명) 그대로. |
| `AnalysisApiResponse` | **typed 응답 DTO**(record). 값이 없는 최상위 필드는 생략(`@JsonInclude(NON_NULL)`). 중첩 `LawHeader`·`LawBrief`·`CitedSource`·`Discovery`(`candidates`·`profileApplied`·`profileBlocked`, 값 없으면 생략, D70). |
| `CitedSourceResolver` | 인용키 → 근거 라벨·원문. 형식 `LAW:{lawId}[@{efYd}][:art:{no} \| :addenda:{no} \| :amend][#{n}]` — 시행일이 없으면 시행중 기준선(`findBaseline`). 조문은 "법령명 제N조(제목)"·본문 + **찾은 법령 버전이 시행예정일 때만** 변경 유형(`changeType`)과, 개정·삭제·이동 조문이면 **현행 원문**(`previousText` — 기준선의 같은 번호, 이동은 옛 번호) — 시행중·과거·철회 버전은 인용키에 날짜가 있어도 둘 다 null, 부칙은 "법령명 부칙 {no}", `:amend`는 "개정문", 접미사가 없으면 "개정 이유". 형식이 다르거나 찾지 못하면 `label=sourceId`, `text=null`. 같은 법령 조회는 요청 안에서 한 번만. |
| `AnalyzeApiRequest` / `LawRefDto` | 요청 `{query, lawRef?, scope?, profileSearch?}`(`profileSearch` 기본 false, D70). `LawRefDto`→`plan.LawRef` 변환(`explicitRef()`). |
| `ActionRevisionController` | `POST /api/v1/analyses/action-revisions`(D71) — 로그인 세션 필수·**CSRF 보호 유지**(`/analyses` 처럼 예외로 두지 않는다). 요청의 답을 세션 질문(`SituationQuestions`)과 함께 [[ActionRevisionUseCase]] 에 넘기고 `RevisionOutcome` 을 응답으로 매핑. 답 검증 실패(`IllegalArgumentException`)는 400. |
| `ActionRevisionApiRequest` / `ActionRevisionApiResponse` | 요청 `{lawRef, answers[{questionId, answer}]}` · 응답 `{action?, stepNotes?, sources?, unmet?}`(값 없는 필드 생략). `action` 은 `ImpactResult` 그대로(대응 방안), `sources` 는 `CitedSourceResolver` 로. |
| `HttpSessionSituationQuestions` | `SituationQuestions` 포트의 HTTP 세션 어댑터 — 세션 속성 하나에 법령 버전(`lawId@yyyy-MM-dd`) → 질문 목록, 최근 20개 LRU. 세션에는 **질문만**(답 없음). 첫 Layer B 응답에 질문이 있고 ACTION 이 채워졌을 때만 `AnalysisController` 가 `save`(근거 부족이면 저장하지 않고 기존 질문도 그대로 둔다). |
| `ApiExceptionHandler` | `@RestControllerAdvice` — 검증 실패→400([[service-api-spec]] §4.1: **시스템 오류만 4xx**). |

> **왜 웹 매퍼가 `LawSource`를 읽나**: 법령명·근거 원문은 화면 표시용 참조 데이터라 표현 매핑의 일부로 둔다. 디스패처도 같은 정본을 읽으므로 요청당 조회가 1~2회 늘어난다 — 응답 지연에 비해 작다(ponytail: 문제가 되면 디스패치 결과에 정본을 실어 재사용).

## HTTP Contract ([[service-api-spec]] §3.0)

- `POST /api/v1/analyses` · 요청 `{ "query": <필수>, "lawRef"?: {lawId, effectiveDate, articleNo?}, "scope"?: [...], "profileSearch"?: boolean }` — `profileSearch`(기본 false)는 법령 발견에서 프로필 라벨을 검색어에 붙이는 토글(D70).
- 세션 쿠키가 있으면 컨트롤러가 CurrentUser.userId 로 유스케이스에 넘긴다(익명은 empty). 응답 unmet 사유에 CONSENT_OUTDATED·DAILY_LIMIT·INSUFFICIENT_GROUNDING 추가(D69).
- **응답은 해소 4상태·분석 모두 HTTP 200** (4xx/5xx는 시스템 오류 전용, §4.1). 빈 `query` → **400**.
- **Analyzed** → `{ resolution:"RESOLVED", law:{ref,lawId,title,effectiveDate,lawType,ministry}, alternatives:[LawBrief], answer:{ <차원소문자>: ImpactResult }, unmet:{ <차원소문자>: {reason, message} }, sources:[CitedSource], disclaimer }`. `reason` ∈ `LAW_NOT_LOADED`·`BASELINE_NOT_LOADED`·`PROFILE_REQUIRED`·`NOT_IMPLEMENTED`·`CONSENT_OUTDATED`·`DAILY_LIMIT`·`INSUFFICIENT_GROUNDING`.
- **Discovered**(D70, Discovery 질문) → `{ resolution:"RESOLVED", discovery:{ candidates:[{lawId,title,effectiveDate,ministry,summary,summaryReady,matched:{sourceId,label}}], profileApplied, profileBlocked? } }` — `law`·`answer`·`unmet`·`sources`·`disclaimer` 없음. `profileBlocked` 는 `Unmet`(`reason`·`message`, `PROFILE_REQUIRED`·`CONSENT_OUTDATED`)이며 토글이 켜졌는데 프로필을 못 쓴 경우에만. LOOKUP 결과는 `unmet` 이 아니라 `discovery` 에만 담긴다.
- **상황 질문**(D71) — Layer B 가 채워지고 질문이 있을 때만 응답에 `situation?: { questions:[{id,text,basis,answerType,options}], stepNotes:[{index,note}] }`. `answerType` ∈ `YES_NO`·`DATE`·`CHOICE`. 없으면 필드 생략.
- **Unresolved** → `{ resolution:<NOT_FOUND_YET|AMBIGUOUS|UNVERIFIED>, message, candidates? }`.

### `POST /api/v1/analyses/action-revisions` (D71, 대응 방안 상황 확인)

- 요청 `{ "lawRef": {lawId, effectiveDate}, "answers": [{ "questionId", "answer" }] }` — 질문 문장은 보내지 않는다(서버가 세션 질문을 쓴다). 로그인 세션·CSRF 토큰 필요(로그인 없으면 401, CSRF 토큰 없으면 403).
- 응답 200 `{ action?, stepNotes?, sources?, unmet? }` — 성공이면 `action`(재생성된 대응 방안 `ImpactResult`)·`stepNotes[{index,note}]`·`sources`, 못 하면 `unmet: { reason, message }`(단수). `reason` ∈ `PROFILE_REQUIRED`·`CONSENT_OUTDATED`·**`SITUATION_EXPIRED`**(신규, 세션에 그 법령 질문 없음)·`LAW_NOT_LOADED`·`BASELINE_NOT_LOADED`·`DAILY_LIMIT`·`INSUFFICIENT_GROUNDING`.
- **400** 은 위조·형식 위반만 — 모르는 `questionId`·중복·답 형식 위반·빈 목록·전부 `모름`(정상 사용으론 불가). 게이트 순서·한도는 [[ActionRevisionUseCase]].
- **OpenAPI** — springdoc이 `/v3/api-docs`(경로 `/api/**`만)를 만든다. `OpenApiSnapshotTest`가 레포 `api/openapi.json`과 비교해 계약 변경을 드러낸다. 운영은 `SPRINGDOC_API_DOCS_ENABLED=false`.

## Error Handling
- 빈/누락 `query`·(재생성) 위조·형식 위반 답 → 400(`ApiExceptionHandler`). 해소 실패·근거 부족은 예외가 아니라 200 본문(resolution/unmet).
- `scope`는 이번 증분 미사용(후속).

## Side Effects
- 정본 읽기(`LawSource`) — 법령 헤더·근거 원문. 쓰기 없음.

## 검증
- `AnalysisResponseMapperTest` — Analyzed(법령 헤더·정본 미적재 헤더·alternatives·answer 순서·unmet·sources·disclaimer) · Unresolved(candidates) · JSON 직렬화(camelCase·null 생략).
- `CitedSourceResolverTest` — 조문(현행 원문 포함)·부칙·개정문·개정 이유·기준선(시행일 없음)·하위 청크(`#n`)·형식 불일치·조문 없음·중복 제거.
- `AnalysisControllerTest` — 위임·상태(200)·빈 query 예외 · 첫 Layer B 질문의 세션 보관·`situation` 직렬화(D71).
- `ActionRevisionControllerTest`(API 슬라이스) — 200 · unmet 사유별(`SITUATION_EXPIRED` 포함) · 400 · CSRF 없으면 403 · 로그인 없으면 거부.
- `OpenApiSnapshotTest` — `/v3/api-docs` = `api/openapi.json`.

## 의존 / 관련
[[AnalyzeUseCase]](application·유스케이스) · [[ActionRevisionUseCase]](상황 재생성) · [[service-api-spec]] §3.0 · [[component-specs]] §4 #8 · [[frontend-stack]](웹 TS 타입 생성).
