---
title: Account API — 계정 REST 어댑터 (슬라이스 ②, web)
status: Draft
version: 0.1
date: 2026-09-27
tags: [component, web, rest, account, privacy]
related: ["components/application/AccountUseCase.md", "components/auth/Auth.md", "components/web/ConsentApi.md", "mvp/service-api-spec.md"]
---

# Account API (web 계층, HTTP 어댑터)

> 로그인한 계정의 조회와 **계정 삭제(파기)**. 규칙은 [[AccountUseCase]], 여기는 HTTP·세션만.

## Responsibility
- **담당**: 세션 `userId`로 [[AccountUseCase]] 위임 · 계정 삭제 시 **세션 무효화 + `JSESSIONID` 삭제**(HTTP 관심사).
- **담당 안 함**: 계정 영속([[Auth|AccountStore]]) · 알림 이메일 동의([[ConsentApi]]).

## HTTP Contract — `com.lia.core.web`
| 메서드 | 경로 | 동작 | 응답 |
|---|---|---|---|
| `GET` | `/api/v1/account` | `{provider, createdAt}` — **userId·email 없음**. SPA의 "로그인 상태 확인"에도 쓴다(200 = 로그인, 401 = 아님) | 200 |
| `DELETE` | `/api/v1/account` | 계정 삭제 → 프로필은 cascade로 함께 파기, 세션 무효화, 쿠키 삭제 | 204 |

- 인증 필수, `DELETE`는 CSRF 토큰 필요.
- 삭제는 되돌릴 수 없다. 다음 로그인은 새 `userId`의 새 계정이다.
- 알림 이메일은 이 응답에 넣지 않는다 — 동의 상태와 함께 [[ConsentApi]] `GET /consents`가 보여준다.

## 구조 (컴포넌트)
| 클래스 | 역할 |
|---|---|
| `AccountController` | `@RestController` `/api/v1/account` — GET/DELETE. |

## Error Handling
- 미인증 → 401. 세션에 `userId` 없음 → 401. 세션 `userId`의 계정이 이미 없음(동시 삭제 등) → `GET` 401(세션이 가리키는 계정이 없으므로 로그인 상태가 아니다), `DELETE` 204(멱등).

## Side Effects
- 세션 무효화·쿠키 삭제(HTTP). DB 쓰기는 [[AccountUseCase]] 경유.

계정 삭제는 **현재 세션만** 파기한다 — 다른 기기의 세션은 남지만 계정에 묶인 호출은 401 이 된다(인메모리 세션 저장소는 사용자별 세션 조회가 안 됨; Spring Session 도입 시 `FindByIndexNameSessionRepository` 로 전 세션 파기).

## 검증
- 웹 슬라이스(MockMvc): GET에 userId·email 없음 · DELETE 204 + 세션 무효화 · 삭제 후 프로필도 사라짐(가짜 store에서 cascade 흉내 대신, cascade 자체는 `UserProfileStore` 통합 테스트가 실 DB로 검증).

## 의존 / 관련
[[AccountUseCase]] · [[Auth]] · [[ConsentApi]] · [[service-api-spec]] §3.4 · D41·D61.
