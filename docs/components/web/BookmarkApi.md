---
title: Bookmark API — REST 어댑터 (⑥a 관심 법령 북마크, web)
status: Draft
version: 0.1
date: 2026-10-30
tags: [component, web, rest, bookmark]
related: ["components/web/ProfileApi.md", "components/web/LawCatalogApi.md", "components/auth/Auth.md", "mvp/service-api-spec.md"]
---

# Bookmark API (web 계층, HTTP 어댑터)

> 로그인 사용자의 **관심 법령 북마크**(추가·해제·목록, [[service-api-spec]] §3.8, D72). 북마크 단위는 **법령(lawId)** 이고 버전이 아니다. 세션 `userId`에 귀속해 `BookmarkUseCase`에 위임한다. HTTP 경계만 담당([[ProfileApi]]와 동일 패턴).

## Responsibility
- **담당**: `/api/v1/bookmarks` 요청 파싱 → 세션 `userId`로 `BookmarkUseCase` 위임 → 결과를 응답·상태코드로 변환, 목록의 상태 라벨 접기(`BookmarkListResponse`).
- **담당 안 함**: 추가 순서·잠금·트랜잭션·한도 판단·보여 줄 버전 SQL(`LawBookmarkStore`) · 결과 enum → 예외 변환(`BookmarkUseCase`) · 인증/세션([[Auth]]) · 알림(구독, 북마크와 별개).

## Collaborators
- [[Auth]] — `CurrentUser`로 세션 `userId`. 없으면 401.
- `BookmarkUseCase` — add / remove / list. 저장소의 추가 결과(enum)를 예외로 바꾼다.
- `LawBookmarkStore` — `law_bookmarks` 읽기·쓰기, 계정 행 잠금, 법령 존재 확인, 한도 판단, 한 트랜잭션.

## HTTP Contract — `com.lia.core.web`
| 메서드 | 경로 | 동작 | 응답 |
|---|---|---|---|
| `PUT` | `/api/v1/bookmarks/{lawId}` | 북마크 추가. 멱등 | 204(이미 있어도 204) / **404** `not_found`(저장된 버전이 하나도 없는 법령) / **409** `bookmark_limit` |
| `DELETE` | `/api/v1/bookmarks/{lawId}` | 북마크 해제. 멱등 | 204(없어도 204) |
| `GET` | `/api/v1/bookmarks` | 내 북마크 목록 — 추가한 시각 최근 순 | 200 `{ "items": [BookmarkItem] }` |

```jsonc
// GET /api/v1/bookmarks → 200
{ "items": [
  { "lawId": "001809", "title": "주택법", "status": "시행예정",
    "effectiveDate": "2026-11-08", "bookmarkedAt": "2026-10-30T00:12:00Z" } ] }
// status ∈ 시행예정 | 시행됨 | 철회
```

```jsonc
// PUT /api/v1/bookmarks/{lawId} → 409
{ "error": "bookmark_limit", "message": "북마크는 최대 100개까지 저장할 수 있어요." }
```
- 인증 필수(익명 401), 쓰기(PUT·DELETE)는 CSRF 토큰 필요(실패 403, [[SecurityConfig]]).
- ☆ 상태는 **목록 응답 하나로 판단**한다 — 단건 조회 API가 없다(D72 ⑤).
- 한도는 `lia.bookmarks.max-per-user`(기본 100, 환경 변수 `LIA_BOOKMARKS_MAX_PER_USER`). 오류 메시지의 숫자는 설정값.

## 구조 (컴포넌트)
| 클래스 | 역할 |
|---|---|
| `BookmarkController` | `@RestController` `/api/v1/bookmarks` — PUT/DELETE/GET. HTTP 경계만: 세션 `userId` → `BookmarkUseCase` 위임, PUT·DELETE 는 `@ResponseStatus(NO_CONTENT)`. |
| `BookmarkUseCase` | 저장소의 추가 결과 enum(`AddResult`) → 예외(`UnknownLawException`·`BookmarkLimitException`·`AccountNotFoundException`). 모르는 결과는 `IllegalStateException`. 규칙은 갖지 않는다. |
| `LawBookmarkStore` | `law_bookmarks`(user_id, law_id, created_at) 저장소. **추가 순서·계정 행 `FOR UPDATE` 잠금·트랜잭션·한도 판단**, 삭제, 목록(보여 줄 버전 조인). |
| `BookmarkListResponse` | 저장소 행(법령 상태 원문) → `BookmarkItem` 응답. 상태 라벨 접기. |

계층: `BookmarkController` → `BookmarkUseCase` → `LawBookmarkStore`.

## 규칙

### 추가 순서 (`LawBookmarkStore.add`, 한 트랜잭션)
1. 계정 행 `FOR UPDATE` — 같은 사용자의 동시 추가를 직렬화해 한도를 넘지 않게 한다. 계정 없음 → `ACCOUNT_NOT_FOUND` → **401**(삭제된 계정의 세션).
2. 이미 북마크함 → `ALREADY`(멱등, 204).
3. 법령 행이 하나도 없음 → `LAW_NOT_FOUND` → **404** `not_found`.
4. 현재 개수 ≥ 한도 → `LIMIT` → **409** `bookmark_limit`.
5. 삽입 → `ADDED`(204).

멱등 확인이 한도 확인보다 앞이다 — 한도에 찬 사용자가 이미 북마크한 법령을 다시 눌러도 오류가 아니다.

### 보여 줄 버전 (목록 SQL 규칙, D72 ②)
법령(lawId)마다 한 버전을 고른다.
- **시행예정본이 있으면 가장 이른 시행예정본** → `status = 시행예정`.
- 없으면 **시행일이 가장 늦은 행** → 그 행의 상태가 `시행중`·`과거`면 `시행됨`, `철회`면 `철회`.
- `effectiveDate` 는 고른 버전의 시행일. S05 경로 `/laws/{lawId}/{effectiveDate}` 가 된다.
- 행은 배치(D62)가 보존하므로 시행·철회 뒤에도 S05 가 열린다.

### 상태 라벨 접기 (`BookmarkListResponse`)
저장소는 법령 상태 원문(`시행예정`·`시행중`·`과거`·`철회`)을 내주고, 응답 단계에서 `시행예정` → `시행예정`, `시행중`·`과거` → `시행됨`, `철회` → `철회` 로 접는다. 화면의 배지 문구(`D-n 시행예정` 등)는 프런트가 `effectiveDate` 로 만든다.

## Error Handling
| 상황 | 응답 |
|---|---|
| 미인증 | **401** `unauthenticated`([[SecurityConfig]]) |
| 세션이 가리키는 계정 없음(`ACCOUNT_NOT_FOUND`) | **401** — 404·409 로 보이지 않는다 |
| 쓰기 CSRF 실패 | **403** |
| 저장된 버전이 하나도 없는 법령(`LAW_NOT_FOUND`) | **404** `not_found` |
| 한도 초과(`LIMIT`) | **409** `bookmark_limit` — 403 이 아닌 이유: 403 은 CSRF 실패에도 쓰여 SPA 가 구분하지 못한다 |

오류 본문 형식은 `{ "error": …, "message": … }`(`ApiExceptionHandler` 재사용).

## Side Effects
- `law_bookmarks` 행 삽입·삭제. `accounts` 삭제 시 `ON DELETE CASCADE` 로 함께 지워진다(D41, D72 ③).
- 법령·분석·구독 데이터는 건드리지 않는다.

## 검증
테스트는 계층별로 귀속한다.
- `LawBookmarkStoreIntegrationTest` — 추가 순서·잠금(실제 DB)·보여 줄 버전 선택.
- `BookmarkUseCaseTest` — 결과 enum → 예외 변환.
- `BookmarkApiTest` — HTTP·인증·CSRF·상태 라벨.

## 의존 / 관련
- [[Auth]](세션 userId) · [[ProfileApi]](동일 web 패턴·ApiExceptionHandler 재사용) · [[LawCatalogApi]](S05 경로 `/laws/{lawId}/{efYd}`)
- [[service-api-spec]] §3.8 · decision-log D72 · [[screen-plan]] S14
