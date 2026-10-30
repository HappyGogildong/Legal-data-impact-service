---
title: History API — REST 어댑터 (⑥b 질의·분석 이력, web)
status: Draft
version: 0.1
date: 2026-10-31
tags: [component, web, rest, history]
related: ["components/web/BookmarkApi.md", "components/web/AnalysisApi.md", "components/auth/Auth.md", "mvp/service-api-spec.md"]
---

# History API (web 계층, HTTP 어댑터)

> 로그인 사용자의 **질의·분석 이력**(저장·목록·상세·삭제, [[service-api-spec]] §3.9, D73). 저장은 **명시적**이고, 저장 내용은 서버가 세션에 보관한 분석 결과의 **전체 스냅샷**이다 — 화면이 내용을 보내지 않는다. 세션 `userId`에 귀속해 `HistoryUseCase`에 위임한다. HTTP 경계만 담당([[BookmarkApi]]와 동일 패턴).

## Responsibility
- **담당**: `/api/v1/history` 요청 파싱 → 세션 `userId`로 `HistoryUseCase` 위임 → 세션 항목으로 스냅샷 JSON 조립(`HistoryController`) → 결과를 응답·상태코드로 변환.
- **담당 안 함**: 저장 순서·잠금·멱등·한도 판단·revision 읽기·"법령 갱신됨" 판정(`AnalysisHistoryStore`) · 결과 enum → 예외 변환(`HistoryUseCase`) · 분석 응답 만들기([[AnalysisApi]]) · 인증/세션([[Auth]]). application·store 는 스냅샷을 **불투명한 JSON 문자열**로만 다룬다(내용을 해석하지 않는다).

## Collaborators
- [[Auth]] — `CurrentUser`로 세션 `userId`. 없으면 401.
- `HttpSessionRecentAnalyses` — 세션에 최근 분석 결과를 보관. `POST /analyses`·`POST /analyses/action-revisions` 가 채우고 `HistoryController` 가 읽는다.
- `HistoryUseCase` — save / list / get / delete. 저장소의 결과(enum)를 예외로 바꾼다.
- `AnalysisHistoryStore` — `analysis_history` 읽기·쓰기, 계정 행 잠금, 멱등·한도 판단, 저장한 버전의 현재 revision·상태 읽기, 한 트랜잭션.

## HTTP Contract — `com.lia.core.web`
| 메서드 | 경로 | 동작 | 응답 |
|---|---|---|---|
| `POST` | `/api/v1/history` | 이력 저장 — 본문 `{ "analysisId": "…" }` 뿐 | 201 `{ "id": 12 }`(새로 저장) / 200 같은 본문(같은 `analysisId` 를 이미 저장) / **400**(`analysisId` 없음·빈 값) / **409** `analysis_expired` / **409** `history_limit` |
| `GET` | `/api/v1/history` | 내 이력 목록 — 저장 최근 순 | 200 `{ "items": [HistoryItem] }` |
| `GET` | `/api/v1/history/{id}` | 이력 상세 | 200 `HistoryDetail` / **404** `not_found`(없거나 남의 기록) |
| `DELETE` | `/api/v1/history/{id}` | 이력 삭제. 멱등, 남의 것은 지우지 않는다 | 204(없어도 204) |

```jsonc
// GET /api/v1/history → 200
{ "items": [
  { "id": 12, "query": "주택법 바뀌면 전세 계약 어떻게 돼?", "lawId": "001809", "lawTitle": "주택법",
    "effectiveDate": "2026-11-08", "savedAt": "2026-10-30T00:12:00Z", "lawUpdated": false } ] }
```

```jsonc
// GET /api/v1/history/{id} → 200  (HistoryDetail = HistoryItem + 아래)
{ "id": 12, "query": "…", "lawId": "001809", "lawTitle": "주택법", "effectiveDate": "2026-11-08",
  "savedAt": "2026-10-30T00:12:00Z", "lawUpdated": true,
  "body": { /* 분석 응답(§3.0) 화면에 보낸 그대로, analysisId 제외 */ },
  "revision": { /* 대응 방안 재생성 응답(§3.0.1), 재생성했을 때만 — 없으면 키 생략 */ },
  "answers": [ { "question": "계약을 2026-11-01 이전에 맺었나요?", "answer": "예" } ],
  "latestEffectiveDate": "2027-02-01" }   // 가장 이른 시행예정본, 없으면 키 생략
```

```jsonc
// POST /api/v1/history → 409
{ "error": "analysis_expired", "message": "저장할 결과가 만료됐어요. 다시 분석한 뒤 저장해 주세요." }
{ "error": "history_limit", "message": "이력은 최대 100개까지 저장할 수 있어요." }
```
- 인증 필수(익명 401), 쓰기(POST·DELETE)는 CSRF 토큰 필요(실패 403, [[SecurityConfig]]).
- 한도는 `lia.history.max-per-user`(기본 100, 환경 변수 `LIA_HISTORY_MAX_PER_USER`). 오류 메시지의 숫자는 설정값.
- 저장 요청은 `analysisId` 만 받는다 — 질문·결과를 본문으로 받지 않으므로 화면이 내용을 바꿔 저장할 수 없다(D73 ③).

## 세션 보관 (`HttpSessionRecentAnalyses`)
- 서버가 분석 결과를 **HTTP 세션(메모리)** 에 보관한다 — DB 아님. 상황 질문(D71)과 같은 방식.
- **최근 20개**, 접근 순 LRU. 가득 차면 가장 오래 쓰지 않은 항목부터 버린다.
- **로그인 사용자의 법령이 정해진 분석만** 담는다: `CurrentUser.userId(request)` 존재 + `AnalysisOutcome.Analyzed` + `Target.Reference` + 응답 `law` 있음 + 법령 발견 응답 아님. 그 밖의 분석 응답엔 `analysisId` 키가 없다.
- 항목 = 질문 원문 + 응답(`AnalysisApiResponse`, 화면에 보낸 그대로) + 재생성본(`ActionRevisionApiResponse`, 없으면 null) + 실제로 쓴 상황 답(`question`·`answer`).
- **재생성 연결** — `POST /analyses/action-revisions` 요청의 선택 필드 `analysisId` 가 있고, **재생성이 성공하고 같은 법령(`lawId`·`effectiveDate`)** 일 때만 그 항목의 재생성본·답을 덮어 쓴다. 실패(`unmet`)·다른 법령이면 붙이지 않는다.
- 재생성을 연결하지 않고 저장하면 상세 응답에 `revision` 키가 없고(없으면 생략) `answers` 는 빈 목록이다.

## 구조 (컴포넌트)
| 클래스 | 역할 |
|---|---|
| `HistoryController` | `@RestController` `/api/v1/history` — POST/GET/GET/DELETE. HTTP 경계: 세션 `userId` → 세션 항목(`HttpSessionRecentAnalyses`)으로 **스냅샷 JSON 조립** → `HistoryUseCase` 위임. POST 는 신규 201·기존 200. |
| `HistoryUseCase` | 저장소의 저장 결과 enum → 예외(분석 만료·한도 초과·계정 없음). 규칙은 갖지 않는다. |
| `AnalysisHistoryStore` | `analysis_history` 저장소. **계정 행 `FOR UPDATE` 잠금·멱등·한도·트랜잭션**, 삭제, 목록·상세, 저장한 버전의 현재 `revision`·`status` 읽기와 `lawUpdated` 판정, `latestEffectiveDate` 조회. 스냅샷은 JSON 문자열 그대로 저장·반환. |
| `HttpSessionRecentAnalyses` | 세션의 최근 분석 결과 보관(위 절). |

계층: `HistoryController` → `HistoryUseCase` → `AnalysisHistoryStore`. 스냅샷 `{ body, revision, answers }` 는 컨트롤러에서 만들고, 그 아래 계층은 내용을 모른다.

## 규칙

### 저장 순서 (`AnalysisHistoryStore`, 한 트랜잭션)
1. 계정 행 `FOR UPDATE` — 같은 사용자의 동시 저장을 직렬화해 한도를 넘지 않게 한다. 계정 없음 → **401**(삭제된 계정의 세션).
2. 같은 `analysis_id` 가 이미 있음 → 그 `id` 로 `ALREADY`(멱등, 200).
3. 그 법령 버전 `(law_id, effective_date)` 의 행이 없음(만료) → **409** `analysis_expired`. 저장 당시 revision 을 읽을 수 없어 "갱신됨" 판정의 기준이 없기 때문이다.
4. 현재 개수 ≥ 한도 → **409** `history_limit`.
5. 삽입(저장 당시 `revision`·`status` 함께 기록) → `SAVED`(201).

멱등 확인이 한도 확인보다 앞이다 — 한도에 찬 사용자가 이미 저장한 분석을 다시 눌러도 오류가 아니다. 세션에 그 `analysisId` 가 없으면 저장소를 부르기 전에 `analysis_expired` 로 끝낸다.

### 법령 갱신됨 (`lawUpdated`, D73 ⑤)
저장한 버전 `(law_id, effective_date)` 의 **지금** `revision`·`status` 가 저장 당시와 다르거나 그 행이 없으면 `true`. 내용 변경·시행·철회를 한 규칙으로 본다.

### 다시 분석할 버전 (`latestEffectiveDate`, D73 ④)
그 법령의 가장 이른 `시행예정` 시행일. 없으면 키를 생략한다. 화면은 있으면 `lawRef { lawId, effectiveDate: latestEffectiveDate }` + 같은 질문으로 S02 를 열고, 없으면 "법령 상세 보기"(저장한 버전 S05). 상황 질문은 새로 답하므로 저장한 답을 다시 보내지 않는다.

## Error Handling
| 상황 | 응답 |
|---|---|
| 미인증 | **401** `unauthenticated`([[SecurityConfig]]) |
| 세션이 가리키는 계정 없음 | **401** — 404·409 로 보이지 않는다 |
| 쓰기 CSRF 실패 | **403** |
| `analysisId` 없음·빈 값 | **400** `bad_request` |
| 이력이 없거나 남의 기록(`GET`) | **404** `not_found` — 남의 기록과 없는 기록을 구분하지 않는다 |
| 세션에 그 분석 없음 · 그 법령 버전 행 없음 | **409** `analysis_expired` |
| 한도 초과 | **409** `history_limit` — 403 이 아닌 이유: 403 은 CSRF 실패에도 쓰여 SPA 가 구분하지 못한다 |

오류 본문 형식은 `{ "error": …, "message": … }`(`ApiExceptionHandler` 재사용).

## Side Effects
- `analysis_history` 행 삽입·삭제. `accounts` 삭제 시 `ON DELETE CASCADE` 로 함께 지워진다(D41, D73 ②).
- 세션의 최근 분석 보관 항목 읽기(저장)·갱신(재생성 연결). 법령·분석·구독·북마크 데이터는 건드리지 않는다.
- 스냅샷에 질문 원문·상황 답이 들어가므로 로그·트레이스에 남기지 않는다(D41).

## 검증
테스트는 계층별로 귀속한다.
- `HttpSessionRecentAnalysesTest` — 최근 20개 LRU·로그인/법령 분석만 보관·재생성 연결 조건.
- `AnalysisControllerTest` · `ActionRevisionControllerTest` — 분석 응답의 `analysisId`, 재생성 요청의 `analysisId` 연결.
- `AnalysisHistoryStoreIntegrationTest` — 저장 순서·잠금(실제 DB)·멱등·한도·revision 읽기·`lawUpdated`·`latestEffectiveDate`.
- `HistoryUseCaseTest` — 결과 enum → 예외 변환.
- `HistoryApiTest` — HTTP·인증·CSRF·오류 매핑·남의 기록 격리.

## 의존 / 관련
- [[Auth]](세션 userId) · [[BookmarkApi]](동일 web 패턴·ApiExceptionHandler 재사용) · [[AnalysisApi]](분석 응답·`analysisId`) · 대응 방안 재생성([[service-api-spec]] §3.0.1)
- [[service-api-spec]] §3.9 · decision-log D73 · [[screen-plan]] S13
