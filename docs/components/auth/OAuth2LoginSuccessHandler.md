---
title: OAuth2LoginSuccessHandler — 신원 매핑·세션 주입 스펙 (구현)
status: Draft
date: 2026-09-20
tags: [component, auth, oauth2, session, provider]
related: ["components/auth/Auth.md", "components/auth/SecurityConfig.md", "components/profile/UserProfile.md", "adr/decision-log.md"]
---

# OAuth2LoginSuccessHandler

> 소셜 로그인 성공 시 **provider 신원 → 내부 `userId`** 로 매핑해 세션에 심는다(Auth, D60). 개요는 [[Auth]], 필터체인 배선은 [[SecurityConfig]]. 패키지: `com.lia.core.auth`.

## Responsibility
- **담당:** 인증 성공 principal에서 `(provider, subject, email?)` 추출 → [[Auth|AccountStore]] 회수/생성 → `userId` 를 세션에 저장 → 리다이렉트.
- **담당 안 함:** 인가 규칙([[SecurityConfig]]) · 계정 SQL([[Auth|AccountStore]]) · 세션에서 `userId` **읽기**(CurrentUser).

## Collaborators
- `AccountStore`(생성자 주입) — `findByProvider` → 없으면 `create`.
- `SavedRequestAwareAuthenticationSuccessHandler`(상속) — 원래 요청지 리다이렉트(없으면 `/`).
- **CurrentUser**(같은 패키지) — 세션 키 `USER_ID` 의 **읽기 짝**. 이 핸들러가 쓰고 CurrentUser가 읽는다(키 단일 소스).

## Business Flow
1. `OAuth2AuthenticationToken` 에서 `registrationId`(provider) + principal 획득.
2. provider별 분기로 `(subject, email?)` **정규화**(아래 표).
3. `accountStore.findByProvider(provider, subject)` → 없으면 `create(provider, subject, email)` → `userId`.
4. 세션 속성 `USER_ID` 에 `userId` 저장.
5. `super.onAuthenticationSuccess` → SavedRequest/`/` 리다이렉트.

## provider 속성 분기 (핵심 함정)
세 IdP의 사용자정보 구조가 **다 다르다** — subject/email 위치가 제각각이라 provider별 추출이 필수:

| provider | principal | subject | email 위치 |
|---|---|---|---|
| google | OidcUser | `sub` (top-level) | `email` (top-level) |
| kakao | OAuth2User | `id` (top-level, Long→String) | `kakao_account.email` (**중첩**) |
| naver | OAuth2User | `response.id` (**중첩**) | `response.email` (**중첩**) |

- naver는 사용자정보 전체를 `response` 아래로 감싼다(그래서 `user-name-attribute: response`, [[SecurityConfig]] 아닌 `application-oauth.yml`).
- 미지원 provider → `IllegalStateException`(설정 실수 fail-fast).

## 결정 1 — 후킹 지점: SuccessHandler (UserService 아님)
`oauth2Login` 이 principal을 만든 **직후** 매핑한다. 커스텀 `OAuth2UserService`/`OidcUserService` 로 하면 provider별 principal 래퍼를 새로 만들어야 해 클래스가 는다. SuccessHandler는 완성된 principal에서 값만 꺼내므로 최소.

## 결정 2 — userId 노출: 세션 속성 (커스텀 principal 아님)
Spring principal(OAuth2User/OidcUser)은 **인증용으로 그대로** 두고(`.authenticated()` 가 그걸 본다), 내부 `userId` 는 **세션 속성 하나**(`USER_ID`)로 저장한다. 컨트롤러는 `CurrentUser.userId(request)` 로 읽는다.
- 대안(커스텀 `OAuth2User` 로 userId 래핑)은 더 정석이지만 provider별 래퍼가 필요 → 과함. 세션 속성이 최소이고 충분.

## Invariants
- 한 `(provider, subject)` → 정확히 하나의 `userId`(재로그인은 `findByProvider` 로 **회수**, 새로 만들지 않음 — 멱등).
- `userId` 는 세션에만 심고 **프롬프트에 절대 주입 안 함**(D41·D10).
- `email` 은 미동의/미제공 시 `null`(알림 채널, nullable) — 로그인은 정상 진행.

## Error Handling
- 미지원 provider → `IllegalStateException`.
- 인증 실패(콜백 오류)는 이 핸들러 이전 단계 — Spring이 로그인 페이지로 리다이렉트([[Auth]] §Error Handling).

## Side Effects
- **DB 쓰기**(최초 로그인 시 accounts insert) · 세션 속성 쓰기 · HTTP 리다이렉트.

## 검증
- 단위(Docker 불필요): Fake AccountStore + 실제 principal/토큰 + Mock 요청·응답 — provider 3종 추출·멱등 회수·null email(`OAuth2LoginSuccessHandlerTest`).
- 라이브(수동): 실제 소셜 로그인 왕복(`--spring.profiles.active=oauth` + provider 콘솔 redirect URI `{baseUrl}/login/oauth2/code/{provider}`).
