---
title: Profile API — REST 어댑터 (슬라이스 ②, web)
status: Draft
version: 0.1
date: 2026-09-09
tags: [component, web, rest, profile]
related: ["components/profile/UserProfile.md", "components/auth/Auth.md", "components/web/AnalysisApi.md", "mvp/service-api-spec.md"]
---

# Profile API (web 계층, HTTP 어댑터)

> 자기신고 프로필 CRUD의 **HTTP 인바운드 어댑터**([[service-api-spec]] §3.4). 인증된 세션 `userId`에 귀속해 [[UserProfile|UserProfileStore]]를 노출한다. HTTP 경계만 담당([[AnalysisApi]]와 동일 패턴).

## Responsibility
- **담당**: `/api/v1/profile` 요청 파싱·검증 → 세션 `userId`로 [[UserProfile|UserProfileStore]] 위임 → 응답·상태코드.
- **담당 안 함**: 프로필 불변식·영속([[UserProfile]]) · 인증/세션([[Auth]]).

## Collaborators
- [[Auth]] — 세션 principal에서 `userId`(`CurrentUser`).
- [[UserProfile|UserProfileStore]] — upsert/find/delete.

## HTTP Contract ([[service-api-spec]] §3.4) — `com.lia.core.web`
| 메서드 | 경로 | 동작 | 인증 |
|---|---|---|---|
| `PUT` | `/api/v1/profile` | 프로필 upsert(전부 선택 입력) → 200 | 필수 |
| `GET` | `/api/v1/profile` | 속성 + `updatedAt` 반환(**userId 제외**) → 200 / 없으면 404 | 필수 |
| `DELETE` | `/api/v1/profile` | 파기 → 204 | 필수 |

- 요청/응답 스키마: [[component-specs]] §2 `UserProfile`(속성만). 검증 위반 → **400**. 미인증 → **401**([[Auth]]).
- **응답에 `userId` 미포함**(D41). `age`는 정수.

## 구조 (컴포넌트)
| 클래스 | 역할 |
|---|---|
| `ProfileController` | `@RestController` `/api/v1/profile` — PUT/GET/DELETE. `CurrentUser`의 userId로 위임. HTTP만. |
| `ProfileApiRequest` | PUT 바디(속성) → `UserProfile` 변환. |

## Error Handling
- 검증 실패 → 400(`ApiExceptionHandler` 재사용, [[AnalysisApi]]). 미인증 → 401. GET 미존재 → 404.

## Side Effects
- 없음(위임) — 실제 쓰기는 [[UserProfile|UserProfileStore]].

## 검증
- 단위: `ProfileController`(목 Store·CurrentUser) — PUT→upsert 호출·GET userId 미포함·DELETE 204·검증 400.
- 슬라이스: 미인증 401([[Auth]] 규칙).

## 의존 / 관련
[[Auth]](① 세션 userId) · [[UserProfile]](② 도메인·store) · [[AnalysisApi]](동일 web 패턴·ApiExceptionHandler 재사용) · [[service-api-spec]] §3.4.
