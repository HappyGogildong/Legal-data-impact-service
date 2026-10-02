---
title: SecurityConfig — 인가·필터체인 스펙 (구현)
status: Draft
date: 2026-09-27
tags: [component, auth, security, authorization, csrf, session]
related: ["components/auth/Auth.md", "components/auth/OAuth2LoginSuccessHandler.md", "components/web/ProfileApi.md", "components/web/AnalysisApi.md", "adr/decision-log.md"]
---

# SecurityConfig

> Spring Security `SecurityFilterChain` 한 개로 **인가 규칙·미인증 응답·CSRF·세션·로그아웃·소셜 로그인**을 선언한다(Auth, D60). 개요·계정 모델은 [[Auth]]. 패키지: `com.lia.core.config`(인증 개념상 auth, 배선상 config).

## Responsibility
- **담당:** 엔드포인트 인가 규칙(공개/인증), API 미인증 응답(401), CSRF 정책, 로그아웃, `oauth2Login` 활성화(조건부).
- **담당 안 함:** 로그인 성공 후 신원→`userId` 매핑([[OAuth2LoginSuccessHandler]]) · 계정 영속([[Auth]] AccountStore) · 세션에서 `userId` 읽기(CurrentUser).

## Collaborators
- **Spring Security 7** 필터체인(`@EnableWebSecurity`는 Boot 자동설정이 부여 — 명시 불필요).
- [[OAuth2LoginSuccessHandler]] — `oauth2Login.successHandler` 로 주입(빈은 프로파일 무관 존재, 사용은 프로파일 게이트).
- `ClientRegistrationRepository` — **oauth 프로파일에서만** 존재하는 빈(아래 게이팅 참조).

## 인가 규칙 (결정 + 근거)

Auth.md 인가 표의 **구현이자 근거**. 매처는 위→아래 첫 매칭이 이기므로 **좁은 permitAll 먼저, `anyRequest()` 마지막**.

| 경로 | 정책 | 근거 |
|---|---|---|
| `/actuator/health`·`/prometheus` | 공개 | 관측(헬스체크·스크레이프). 노출 목록이 4개로 좁아 민감 엔드포인트는 애초에 매핑 안 됨 |
| `/oauth2/**`·`/login/**`·`/logout` | 공개 | 로그인 흐름 자체 — 인증 요구하면 로그인 리다이렉트가 막힌다 |
| `/error` | 공개 | **오류 포워드(ERROR 디스패치)도 인가 대상**이다. 막으면 익명의 공개 API 호출이 400/500 대신 빈 403을 받는다 |
| `/api/v1/laws/**` | 공개 | 익명 Layer A 허용(정본 조회) |
| `POST /api/v1/analyses` | 공개 | 익명 Layer A 허용 — 프로필 없으면 Layer B는 `unmet`(D60). **메서드 한정**: GET 등 후속 생겨도 자동 개방 방지 |
| `/api/v1/profile/**` | 인증 | 개인 데이터. **동작상 `anyRequest`와 중복** — Auth.md 표 미러용으로 남긴 문서 규칙 |
| 그 외(`anyRequest`) | 인증 | **화이트리스트 종결** — 이 줄이 없으면 매칭 안 된 경로가 통과된다(Security 6+는 미매칭=abstain=허용) |

> **`anyRequest().authenticated()` 는 보안 경계다.** profile 규칙이 따로 있어도 실제 보호는 이 줄이 한다 — 바꿀 때 주의.

## 미인증 응답 (401)
- `/api/**` 미인증 → **401 + `{"error": "unauthenticated", "message": ...}`**(`defaultAuthenticationEntryPointFor`, JSON 본문을 쓰는 entry point).
- 이유: entry point를 안 정하면 기본 프로파일은 `Http403ForbiddenEntryPoint`(403), oauth 프로파일은 oauth2Login entry point(302 `/login`, HTML)로 떨어진다. SPA가 "로그인 안 됨"(401)과 "권한 없음"(403)을 구분하려면 API는 상태코드여야 한다.
- **본문 모양은 하나다** — 익명 401, 삭제된 계정을 가리키는 세션의 401(아래), 유스케이스의 `AccountNotFoundException` 401이 모두 같은 `{error: unauthenticated}`. SPA는 401 하나만 처리하면 된다.
- `/api/**` 밖의 경로는 기본 entry point를 따른다(oauth 프로파일에서 브라우저 로그인 유도).

## 세션-계정 정합 — 삭제된 계정을 가리키는 세션
계정 삭제는 **현재 세션만** 파기한다(인메모리 세션 저장소는 사용자별 세션 조회가 안 됨). 다른 기기의 세션은 인증된 채로 삭제된 `userId`를 들고 남는다.
→ `StaleSessionFilter`(`SecurityContextHolderFilter` 바로 뒤): `/api/**` 요청에서 세션 `USER_ID`의 계정이 **없으면** 세션을 파기하고 SecurityContext를 비운 뒤 **익명으로 계속 진행**한다.
- 보호 API → 인가 단계에서 위 401(`unauthenticated`). 엔드포인트마다 따로 처리할 필요가 없다(동의 상태가 200 + null로 보이거나 프로필 수정이 409로 보이던 문제 제거).
- 공개 API(`/laws`, `POST /analyses`) → 익명으로 정상 처리(낡은 세션 때문에 공개 기능이 막히지 않는다).
- `/api/**` 밖(로그인 흐름)은 건너뛴다 — 재로그인을 방해하지 않게.
- 비용: 세션에 `USER_ID`가 있는 API 요청마다 accounts 기본키 조회 1회. Spring Session 도입 시 계정 삭제가 전 세션을 파기하면 이 필터는 방어선으로만 남는다.

## CSRF 결정
- **켠다** — 세션-쿠키 인증이라 브라우저가 쿠키를 자동 첨부 → CSRF 공격면 존재.
- **`csrf.spa()`**(Security 7) — `XSRF-TOKEN` 쿠키(JS 읽기 가능) + SPA가 쿠키값을 **원문 그대로** `X-XSRF-TOKEN` 헤더로 보내도 통과. 기본 `XorCsrfTokenRequestAttributeHandler`는 **마스킹된 토큰**을 요구하므로 저장소만 `CookieCsrfTokenRepository`로 바꾸면 SPA 요청이 403이 된다(구현 함정).
- **`ignoringRequestMatchers("/api/v1/analyses")`** — 공개 무상태 POST만 예외. 근거: **세션 인증을 안 쓰는 엔드포인트는 CSRF 공격 대상이 아니다**(공격자가 이용할 세션이 없음). 이걸 빼야 익명 curl/외부 호출이 403 안 난다.
  - ⚠️ **임시 조치** — 슬라이스 ③에서 이 엔드포인트가 세션 `userId`(Layer B 프로필)를 읽게 되면 전제가 깨진다. 그때 예외를 제거하거나 익명/인증 경로를 분리한다(코드에 `ponytail:` 주석).
- **profile·logout 은 예외에 넣지 않는다** — 세션 인증 + 상태변경이라 반드시 토큰 필요. GET(`/laws`)은 CSRF 대상 아님(안전 메서드).

## 세션 정책 · 세션 vs JWT (D60)
- 서버 세션 + 세션 쿠키(**HttpOnly·SameSite=Lax**, `application.yml`). Secure는 미설정 → HTTPS 요청에서만 자동(로컬 HTTP 무해).
- **JWT 아닌 세션-쿠키 선택**: 같은 도메인 웹 UI에 최적 + **즉시 무효화**(로그아웃·파기 D41). JWT는 만료 전 폐기가 어렵다. 확장 시 `spring-session-jdbc`/Redis로 **저장소만** 교체(재설계 아님).
- **로그아웃**(POST `/logout`, CSRF 토큰 필요): 세션 무효화 + `JSESSIONID` 삭제 → D41 파기. 응답은 **204**(`HttpStatusReturningLogoutSuccessHandler`) — SPA는 API 루트가 아니라 프론트에 있어 `/` 리다이렉트는 인증 필요 경로로 떨어진다.

## 부팅 게이팅 (구현 함정)
`oauth2Login()` 은 **`ClientRegistrationRepository` 빈이 있어야** 부팅된다. 이 빈은 client-id가 채워진 `oauth` 프로파일에서만 생성된다(키 없는 dev/CI에서 빈 client-id는 부팅을 깬다 → OAuth 설정을 `application-oauth.yml`로 분리한 이유).

→ `ObjectProvider<ClientRegistrationRepository>.ifAvailable(...)` 안에서만 `oauth2Login` 을 붙인다. **키 없는 기본/CI 부팅은 소셜 로그인 없이 깨끗하게 뜨고**, 키 있는 배포만 `--spring.profiles.active=oauth` 로 활성화.

## Error Handling
- `/api/**` 미인증 → **401**, 인가 실패 → **403**([[service-api-spec]] §4.1 정합). CSRF 실패 → 403.
- `oauth2Login` 구성 예외는 `IllegalStateException` 으로 승격(부팅 실패 = fail-fast).

## Design Constraints
- 규칙 순서 의존성(좁은 것 먼저) — 리팩터 시 `anyRequest()` 는 항상 마지막.

## 검증
- `SecurityConfigTest` — Boot 없이 최소 웹 컨텍스트(`@EnableWebMvc`+`@EnableWebSecurity`+이 설정)와 스텁 컨트롤러를 MockMvc로 검사(Boot 4는 `@WebMvcTest`가 별도 모듈). oauth 프로파일이 아니라 키 없는 부팅 경로 그대로.
  - 익명 보호/미등록 API 401 · 인증 200 · laws·health 공개 · analyses CSRF 예외 · `/error` ERROR 디스패치 통과 · 토큰 없는 상태변경 403 · **SPA 흐름**(GET으로 받은 쿠키값을 헤더에 원문으로) 통과 · 로그아웃 204.
  - 리뷰 전 설정으로 되돌리면 401·`/error`·SPA·로그아웃 테스트 5개가 실패함을 확인(테스트가 실제로 회귀를 잡음).
  - 삭제된 계정을 가리키는 세션: 보호 API 401(`unauthenticated` 본문) + 세션 파기 · 공개 API는 익명으로 정상 처리 · 익명 401도 같은 본문.
