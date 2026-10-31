---
title: Subscription API — REST 어댑터 (⑦a 법령 구독·인앱 알림함, web)
status: Draft
version: 0.1
date: 2026-10-31
tags: [component, web, rest, subscription, notification]
related: ["components/web/BookmarkApi.md", "components/web/HistoryApi.md", "components/auth/Auth.md", "mvp/service-api-spec.md"]
---

# Subscription API (web 계층, HTTP 어댑터)

> 로그인 사용자의 **법령 구독**(만들기·시점 변경·해지·목록)과 **인앱 알림함**(목록·읽음 처리), 그리고 알림을 만드는 매일 배치 `notify`([[service-api-spec]] §3.6, D74). 구독 단위는 **법령(lawId)** 이고 시행본이 아니다 — 그 법령의 모든 시행예정본에 알림이 간다. 세션 `userId`에 귀속해 각 UseCase 에 위임한다. HTTP 경계만 담당([[BookmarkApi]]·[[HistoryApi]]와 동일 패턴).

## Responsibility
- **담당**: `/api/v1/subscriptions`·`/api/v1/notifications` 요청 파싱(`notifyBefore` 값 검증) → 세션 `userId`로 UseCase 위임 → 결과를 응답·상태코드로 변환.
- **담당 안 함**: 구독 추가 순서·잠금·트랜잭션·한도 판단·다음 시행일 SQL(`LawSubscriptionStore`) · 알림 생성·목록·읽음 SQL(`NotificationStore`) · 결과 enum → 예외 변환(각 UseCase) · 배치 단계 호출(`IngestJob`) · 인증/세션([[Auth]]) · 이메일 발송(⑦c) · 내 프로필 관련 구독(⑦b).

## Collaborators
- [[Auth]] — `CurrentUser`로 세션 `userId`. 없으면 401.
- `SubscriptionUseCase` — subscribe / unsubscribe / list. 저장소의 결과(enum)를 예외로 바꾼다.
- `NotificationUseCase` — list / read / readAll.
- `LawSubscriptionStore` — `law_subscriptions` 읽기·쓰기, 계정 행 잠금, 법령 존재 확인, 한도 판단, 목록(다음 시행일 조인), 한 트랜잭션.
- `NotificationStore` — `notifications` 읽기·쓰기. API 쪽(목록·읽지 않은 수·읽음·모두 읽음)과 배치 쪽(`DueNotifications` 구현 — 알림 생성)을 함께 맡는다.
- `DueNotifications` — 배치가 부르는 포트(알림 생성·드라이런 개수). `IngestJob` 이 쓰고 `NotificationStore` 가 구현한다.

## HTTP Contract — `com.lia.core.web`

### 구독
| 메서드 | 경로 | 동작 | 응답 |
|---|---|---|---|
| `PUT` | `/api/v1/subscriptions/{lawId}` | 구독 만들기 / 알림 시점 변경. 본문 `{ "notifyBefore": 7 }` | 204 / **400**(`notifyBefore` 가 0·1·7·30 이 아니거나 없음) / **404** `not_found`(저장된 버전이 하나도 없는 법령) / **409** `subscription_limit` |
| `DELETE` | `/api/v1/subscriptions/{lawId}` | 구독 해지. 멱등 | 204(없어도 204) |
| `GET` | `/api/v1/subscriptions` | 내 구독 목록 — 다음 시행일 가까운 순, 없는 것은 뒤(그 안에서 `createdAt` 최근 순) | 200 `{ "items": [SubscriptionItem] }` |

### 알림
| 메서드 | 경로 | 동작 | 응답 |
|---|---|---|---|
| `GET` | `/api/v1/notifications` | 인앱 알림함 — `createdAt` 최근 순 50개 + 읽지 않은 수 | 200 `{ "items": [NotificationItem], "unreadCount": 3 }` |
| `POST` | `/api/v1/notifications/{id}/read` | 읽음 처리. 멱등, 남의 알림은 무시 | 204 |
| `POST` | `/api/v1/notifications/read-all` | 내 알림 모두 읽음 | 204 |

```jsonc
// GET /api/v1/subscriptions → 200
{ "items": [
  { "lawId": "001809", "title": "주택법", "notifyBefore": 7, "createdAt": "2026-10-31T00:00:00Z",
    "nextEffectiveDate": "2026-11-09" } ] }
// nextEffectiveDate: 가장 이른 시행예정본의 시행일, 없으면 키 생략
// title: 가장 이른 시행예정본의 제목, 없으면 시행일이 가장 늦은 행의 제목
```

```jsonc
// GET /api/v1/notifications → 200
{ "items": [
  { "id": 12, "lawId": "001809", "title": "주택법", "effectiveDate": "2026-11-09", "notifyBefore": 7,
    "createdAt": "2026-11-02T19:00:00Z", "read": false } ],
  "unreadCount": 3 }
```

```jsonc
// PUT /api/v1/subscriptions/{lawId} → 409
{ "error": "subscription_limit", "message": "구독은 최대 100개까지 할 수 있어요." }
```
- 인증 필수(익명 401), 쓰기(PUT·DELETE·POST)는 CSRF 토큰 필요(실패 403, [[SecurityConfig]]).
- 구독 상태("알림 받는 중 · 7일 전")는 **목록 응답 하나로 판단**한다 — 단건 조회 API가 없다(북마크와 같은 방식).
- 한도는 `lia.subscriptions.max-per-user`(기본 100, 환경 변수 `LIA_SUBSCRIPTIONS_MAX_PER_USER`). 오류 메시지의 숫자는 설정값. 이미 구독한 법령의 시점 변경은 한도와 무관.
- `notifyBefore` ∈ {0, 1, 7, 30}(일). 화면 표기는 당일·1일 전·7일 전·30일 전.
- operationId 는 `putSubscription`·`deleteSubscription`·`listSubscriptions`·`listNotifications`·`readNotification`·`readAllNotifications` 로 명시한다(자동 이름에 의존하지 않는다).

## 구조 (컴포넌트)
| 클래스 | 역할 |
|---|---|
| `SubscriptionController` | `@RestController` `/api/v1/subscriptions` — PUT/DELETE/GET. HTTP 경계만: 세션 `userId` → `SubscriptionUseCase` 위임, PUT·DELETE 는 `@ResponseStatus(NO_CONTENT)`. |
| `SubscriptionUseCase` | 저장소의 결과 enum → 예외(법령 없음·한도 초과·계정 없음). 모르는 결과는 `IllegalStateException`. 규칙은 갖지 않는다. |
| `LawSubscriptionStore` | `law_subscriptions`(user_id, law_id, notify_before, created_at) 저장소. **추가 순서·계정 행 `FOR UPDATE` 잠금·트랜잭션·한도 판단**, 삭제, 목록(다음 시행일 조인). |
| `NotificationController` | `@RestController` `/api/v1/notifications` — GET/POST read/POST read-all. 세션 `userId` → `NotificationUseCase` 위임. |
| `NotificationUseCase` | 알림 목록·읽음·모두 읽음을 저장소에 위임. 규칙은 갖지 않는다. |
| `NotificationStore` | `notifications` 저장소 — 목록(최근 50개)·읽지 않은 수·읽음·모두 읽음, 그리고 **알림 생성**(`DueNotifications` 구현). |
| `DueNotifications` | 배치가 부르는 포트 — 알림 생성(만든 수)·드라이런(만들 수). 구현은 `NotificationStore`. |

계층: `SubscriptionController` → `SubscriptionUseCase` → `LawSubscriptionStore` · `NotificationController` → `NotificationUseCase` → `NotificationStore` · 배치 `IngestJob` → `DueNotifications`(구현 `NotificationStore`).

## 규칙

### 구독 추가 순서 (`LawSubscriptionStore.subscribe`, 한 트랜잭션)
1. 계정 행 `FOR UPDATE` — 같은 사용자의 동시 추가를 직렬화해 한도를 넘지 않게 한다. 계정 없음 → `ACCOUNT_NOT_FOUND` → **401**(삭제된 계정의 세션).
2. 이미 구독함 → `notify_before` 만 바꾼다 → `UPDATED`(204). 한도와 무관하다.
3. 법령 행이 하나도 없음 → `LAW_NOT_FOUND` → **404** `not_found`.
4. 현재 개수 ≥ 한도 → `LIMIT` → **409** `subscription_limit`.
5. 삽입 → `SUBSCRIBED`(204).

기존 구독 확인이 한도 확인보다 앞이다 — 한도에 찬 사용자가 이미 구독한 법령의 시점을 바꿔도 오류가 아니다.

### 알림 생성 (배치 `notify`, D74 ③)
구독 법령의 `status = '시행예정'` 버전 중 `시행일 − notify_before ≤ 오늘(서울) ≤ 시행일` 이면 알림 한 건. 중복은 `UNIQUE(user_id, law_id, effective_date, notify_before)` 가 막는다 — 이 테이블이 알림함이자 Outbox([[concurrency-and-reliability]] §3).

```sql
INSERT INTO notifications (user_id, law_id, effective_date, law_title, notify_before, created_at)
SELECT s.user_id, v.law_id, v.effective_date, v.title, s.notify_before, now()
FROM law_subscriptions s
JOIN law_versions v ON v.law_id = s.law_id AND v.status = '시행예정'
WHERE v.effective_date - s.notify_before <= :today AND :today <= v.effective_date
ON CONFLICT (user_id, law_id, effective_date, notify_before) DO NOTHING
```
- `:today` 는 서울 날짜(실행 시점). 삽입된 행 수가 만든 알림 수다.
- "≤ 오늘" 이라 배치가 하루 빠져도 다음 실행에서 한 번 만든다. 시점 안으로 늦게 들어온 새 개정도 다음 실행에서 한 번.
- 시점을 바꾸면 키(`notify_before`)가 달라져 새 시점의 알림이 다시 만들어진다. 이미 시행일이 지난 버전(`시행중`·`과거`)·철회 버전은 대상이 아니다.
- 드라이런(`--dry-run`)은 같은 조건의 `count(*)` 만 센다(삽입 없음).

### 배치 명령 (`IngestJob`)
| 명령 | 동작 | 기록 |
|---|---|---|
| `notify` | 알림 생성 | `counts.notified` — 만든 수 |
| `notify --dry-run` | 삽입 없이 만들 수만 | `counts.planned.notified` |
| `run`(드라이런 아님) | 큐 소진 뒤 **끝 단계**로 notify 를 부른다 | `counts.notified` |

운영 명령 목록은 [[deployment]].

### 목록 규칙
- 구독 `title` 은 가장 이른 시행예정본의 제목, 시행예정본이 없으면 시행일이 가장 늦은 행의 제목. `nextEffectiveDate` 는 가장 이른 시행예정본의 시행일(없으면 키 생략).
- 정렬: 구독은 다음 시행일 가까운 순, 없는 것은 뒤(그 안에서 `createdAt` 최근 순). 알림은 `createdAt` 최근 순 50개, `unreadCount` 는 50개 제한과 무관하게 읽지 않은 전체 수.
- 알림의 `title` 은 생성 시점에 `notifications.law_title` 로 저장한 값이다.

## Error Handling
| 상황 | 응답 |
|---|---|
| 미인증 | **401** `unauthenticated`([[SecurityConfig]]) |
| 세션이 가리키는 계정 없음(`ACCOUNT_NOT_FOUND`) | **401** — 404·409 로 보이지 않는다 |
| 쓰기 CSRF 실패 | **403** |
| `notifyBefore` 가 0·1·7·30 이 아니거나 본문에 없음 | **400** `bad_request` |
| 저장된 버전이 하나도 없는 법령(`LAW_NOT_FOUND`) | **404** `not_found` |
| 한도 초과(`LIMIT`) | **409** `subscription_limit` — 403 이 아닌 이유: 403 은 CSRF 실패에도 쓰여 SPA 가 구분하지 못한다 |
| 남의 알림 읽음 처리 | **204** — 아무것도 바꾸지 않는다(존재 여부를 알리지 않는다) |

오류 본문 형식은 `{ "error": …, "message": … }`(`ApiExceptionHandler` 재사용).

## Side Effects
- `law_subscriptions`·`notifications` 행 삽입·변경·삭제. `accounts` 삭제 시 `ON DELETE CASCADE` 로 함께 지워진다(D41, D74).
- 배치 `notify` 는 `notifications` 에만 쓰고 법령·분석·북마크·이력 데이터는 건드리지 않는다. LLM·외부 호출 없음.
- 알림 문구는 화면이 `effectiveDate`·`notifyBefore` 로 만든다 — 서버는 문구를 저장하지 않는다.

## 검증
테스트는 계층별로 귀속한다.
- `LawSubscriptionStoreIntegrationTest`(실제 DB, Docker 없으면 스킵) — 추가 순서·시점 변경(한도 무관)·해지 멱등·한도 경계·법령 없음·cascade·목록의 다음 시행일·정렬.
- `NotificationStoreIntegrationTest`(실제 DB) — 알림 생성(시점 경계·놓친 날 보충·두 번 실행해도 한 번·철회·시행중·과거 제외·시점 변경 뒤 새 알림·새 개정 즉시 한 번)·드라이런 개수·목록 최근 50개·읽지 않은 수·읽음·모두 읽음·남의 알림 불가.
- `JobCommandTest` · `IngestJobIntegrationTest`(notify) — `notify` 명령 파싱·`counts.notified`/`counts.planned.notified` 기록·`run` 끝 단계 호출·`--dry-run` 은 삽입 없음.
- `SubscriptionUseCaseTest` — 결과 enum → 예외 변환.
- `SubscriptionApiTest` · `NotificationApiTest` — HTTP·인증·CSRF·400/404/409·목록 형식·남의 알림 격리.

## 의존 / 관련
- [[Auth]](세션 userId) · [[BookmarkApi]](동일 web 패턴·계정 행 잠금·한도) · [[HistoryApi]](ApiExceptionHandler 재사용) · [[LawCatalogApi]](S05 경로 `/laws/{lawId}/{efYd}`)
- [[service-api-spec]] §3.6 · decision-log D74 · [[screen-plan]] S11·S12 · [[concurrency-and-reliability]] §3
