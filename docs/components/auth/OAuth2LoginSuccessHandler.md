---
title: OAuth2LoginSuccessHandler — 신원 매핑·세션 주입 스펙 (구현)
status: Draft
date: 2026-09-27
tags: [component, auth, oauth2, session, provider]
related: ["components/auth/Auth.md", "components/auth/SecurityConfig.md", "components/web/ConsentApi.md", "components/profile/UserProfile.md", "adr/decision-log.md"]
---

# OAuth2LoginSuccessHandler

> 소셜 로그인 성공 시 **provider 신원 → 내부 `userId`** 로 매핑해 세션에 심는다(Auth, D60). 개요는 [[Auth]], 필터체인 배선은 [[SecurityConfig]]. 패키지: `com.lia.core.auth`.

## Responsibility
- **담당:** 인증 성공 principal에서 subject 추출(`OAuth2Identity`) → [[Auth|AccountStore]] `findOrCreate` → `userId` 를 세션에 저장 → 프론트로 리다이렉트. subject가 없으면 로그인을 무효화(fail-closed).
- **담당 안 함:** 인가 규칙([[SecurityConfig]]) · 계정 SQL·경합 처리([[Auth|AccountStore]]) · 세션에서 `userId` **읽기**(CurrentUser) · **이메일 저장**(알림 동의 시에만 — [[ConsentApi]], D61).

## Collaborators
- `AccountStore`(생성자 주입) — `findOrCreate(provider, subject)`(경합 안전, [[Auth]] Persistence Contract).
- `OAuth2Identity`(같은 패키지) — provider별 `(subject, email?)` 정규화. **알림 동의([[ConsentApi]])도 같은 클래스로 이메일을 꺼낸다** — provider 분기는 이 한 곳에만 있다.
- `SimpleUrlAuthenticationSuccessHandler`(상속) — 항상 `lia.auth.frontend-url`(env `FRONTEND_URL`)로 리다이렉트.
- **CurrentUser**(같은 패키지) — 세션 키 `USER_ID` 의 **읽기 짝**. 이 핸들러가 쓰고 CurrentUser가 읽는다(키 단일 소스).

## Business Flow
1. `OAuth2AuthenticationToken` 에서 `registrationId`(provider) + principal 획득.
2. `OAuth2Identity.from(provider, principal)`로 `(subject, email?)` **정규화**(아래 표). 없는 값은 null.
3. subject가 비면 → **fail-closed**(아래). 끝.
4. `accountStore.findOrCreate(provider, subject)` → `userId`. **email은 넘기지 않는다** — 로그인 중에는 principal 속성에만 머문다.
5. 세션 속성 `USER_ID` 에 `userId` 저장.
6. 프론트 URL로 리다이렉트.

## provider 속성 분기 (핵심 함정) — `OAuth2Identity`
세 IdP의 사용자정보 구조가 **다 다르다** — subject/email 위치가 제각각이라 provider별 추출이 필수. 이 분기는 `OAuth2Identity` 한 곳에 있고 로그인 핸들러와 알림 동의가 공유한다:

| provider | principal | subject | email 위치 |
|---|---|---|---|
| google | OidcUser(`openid` scope) | `sub` (top-level) | `email` (top-level) |
| kakao | OAuth2User | `id` (top-level, Long→String) | `kakao_account.email` (**중첩**) |
| naver | OAuth2User | `response.id` (**중첩**) | `response.email` (**중첩**) |

- naver는 사용자정보 전체를 `response` 아래로 감싼다(그래서 `application-oauth.yml`의 `user-name-attribute: response`). `response` 자체가 없으면 `DefaultOAuth2User` 생성 단계에서 먼저 실패하므로, 여기까지 오는 누락 사례는 `response` 안에 `id`가 없는 경우다.
- scope는 식별자 + email만 요청한다(이름/닉네임 미요청, D41) — [[Auth]] §프라이버시.
- 미지원 provider → `IllegalStateException`(설정 실수 fail-fast).

## 결정 1 — 후킹 지점: SuccessHandler (UserService 아님)
`oauth2Login` 이 principal을 만든 **직후** 매핑한다. 커스텀 `OAuth2UserService`/`OidcUserService` 로 하면 provider별 principal 래퍼를 새로 만들어야 해 클래스가 는다. SuccessHandler는 완성된 principal에서 값만 꺼내므로 최소.
- 대가: 이 시점엔 인증이 이미 끝나 있어 예외를 던져도 로그인 실패 처리로 가지 않는다 → subject 누락은 **직접 세션을 파기**해 처리한다(아래).

## 결정 2 — userId 노출: 세션 속성 (커스텀 principal 아님)
Spring principal(OAuth2User/OidcUser)은 **인증용으로 그대로** 두고(`.authenticated()` 가 그걸 본다), 내부 `userId` 는 **세션 속성 하나**(`USER_ID`)로 저장한다. 컨트롤러는 `CurrentUser.userId(request)` 로 읽는다.
- 대안(커스텀 `OAuth2User` 로 userId 래핑)은 더 정석이지만 provider별 래퍼가 필요 → 과함. 세션 속성이 최소이고 충분.

## 결정 3 — 리다이렉트: 항상 프론트 (SavedRequest 안 씀)
- SavedRequest를 따르지 않는다. 저장된 요청은 대개 401을 받은 **API URL**이라, 따르면 브라우저가 JSON 화면으로 간다.
- `/` 로도 보내지 않는다 — API 서버 루트는 인증 필요 경로이고 핸들러도 없다. SPA는 프론트(#14)에 있다.

## fail-closed (subject 누락)
인증 필터는 successHandler 호출 **전에** SecurityContext를 세션에 저장한다. 그래서 신원을 못 얻었을 때 그냥 리턴하면 "로그인된 세션"이 남는다.
→ `SecurityContextHolder.clearContext()` + **세션 invalidate** + `{frontend}/login?error=identity` 로 리다이렉트. 계정은 만들지 않는다.

## Invariants
- 한 `(provider, subject)` → 정확히 하나의 `userId`(재로그인·동시 최초 로그인 모두 — `findOrCreate` 가 보장).
- subject 없이는 세션에 로그인 상태도 `userId` 도 남지 않는다.
- `userId` 는 세션에만 심고 **프롬프트에 절대 주입 안 함**(D41·D10).
- **로그인은 이메일을 저장하지 않는다**(D61) — `accounts.email`은 알림 수신 동의로만 채워진다. IdP가 이메일을 안 줘도 로그인은 정상 진행.

## Error Handling
- subject 누락 → fail-closed 리다이렉트(예외 아님).
- 미지원 provider → `IllegalStateException`.
- 인증 실패(콜백 오류)는 이 핸들러 이전 단계 — Spring이 로그인 실패로 처리([[Auth]] §Error Handling).

## Side Effects
- **DB 쓰기**(최초 로그인 시 accounts insert — email 없이) · 세션 속성 쓰기/세션 파기 · HTTP 리다이렉트.

## 검증
- 단위(Docker 불필요, `OAuth2LoginSuccessHandlerTest`): 인메모리 `InMemoryAccountStore` + 실제 principal/토큰 + Mock 요청·응답 — subject 로 계정 회수/생성 · 멱등 회수 · **이메일을 준 IdP여도 계정에 email 미저장** · 프론트 리다이렉트 · subject 누락 시 계정 미생성·세션 파기·오류 리다이렉트.
- 단위(`OAuth2IdentityTest`): provider 3종 `(subject, email)` 추출 · 중첩 속성 누락 시 null · 미지원 provider 예외.
- 라이브(수동): 실제 소셜 로그인 왕복(`--spring.profiles.active=oauth` + provider 콘솔 redirect URI `{baseUrl}/login/oauth2/code/{provider}`).
