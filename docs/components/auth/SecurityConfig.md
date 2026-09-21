---
title: SecurityConfig — 인가·필터체인 스펙 (구현)
status: Draft
date: 2026-09-20
tags: [component, auth, security, authorization, csrf, session]
related: ["components/auth/Auth.md", "components/auth/OAuth2LoginSuccessHandler.md", "components/web/ProfileApi.md", "components/web/AnalysisApi.md", "adr/decision-log.md"]
---

# SecurityConfig

> Spring Security `SecurityFilterChain` 한 개로 **인가 규칙·CSRF·세션·로그아웃·소셜 로그인**을 선언한다(Auth, D60). 개요·계정 모델은 [[Auth]]. 패키지: `com.lia.core.config`(인증 개념상 auth, 배선상 config).

## Responsibility
- **담당:** 엔드포인트 인가 규칙(공개/인증), CSRF 정책, 세션 쿠키 정책, 로그아웃, `oauth2Login` 활성화(조건부).
- **담당 안 함:** 로그인 성공 후 신원→`userId` 매핑([[OAuth2LoginSuccessHandler]]) · 계정 영속([[Auth]] AccountStore) · 세션에서 `userId` 읽기(CurrentUser).

## Collaborators
- **Spring Security** 필터체인(`@EnableWebSecurity`는 Boot 자동설정이 부여 — 명시 불필요).
- [[OAuth2LoginSuccessHandler]] — `oauth2Login.successHandler` 로 주입(빈은 프로파일 무관 존재, 사용은 프로파일 게이트).
- `ClientRegistrationRepository` — **oauth 프로파일에서만** 존재하는 빈(아래 게이팅 참조).

## 인가 규칙 (결정 + 근거)

Auth.md 인가 표의 **구현이자 근거**. 매처는 위→아래 첫 매칭이 이기므로 **좁은 permitAll 먼저, `anyRequest()` 마지막**.

| 경로 | 정책 | 근거 |
|---|---|---|
| `/actuator/health`·`/prometheus` | 공개 | 관측(헬스체크·스크레이프). 노출 목록이 4개로 좁아 민감 엔드포인트는 애초에 매핑 안 됨 |
| `/oauth2/**`·`/login/**`·`/logout` | 공개 | 로그인 흐름 자체 — 인증 요구하면 로그인 리다이렉트가 막힌다 |
| `/api/v1/laws/**` | 공개 | 익명 Layer A 허용(정본 조회) |
| `POST /api/v1/analyses` | 공개 | 익명 Layer A 허용 — 프로필 없으면 Layer B는 `unmet`(D60). **메서드 한정**: GET 등 후속 생겨도 자동 개방 방지 |
| `/api/v1/profile/**` | 인증 | 개인 데이터 |
| 그 외(`anyRequest`) | 인증 | **화이트리스트 종결** — 이 줄이 없으면 매칭 안 된 경로가 통과된다(Security 6는 미매칭=abstain=허용) |

> **`anyRequest().authenticated()` 는 보안 경계다.** 빠지면 위 규칙에 안 걸린 모든 경로가 무방비로 열린다.

## CSRF 결정
- **켠다** — 세션-쿠키 인증이라 브라우저가 쿠키를 자동 첨부 → CSRF 공격면 존재.
- **`CookieCsrfTokenRepository.withHttpOnlyFalse()`** — SPA(#14)가 JS로 `XSRF-TOKEN` 쿠키를 읽어 헤더에 실어야 하므로 HttpOnly 해제. (세션 쿠키 자체는 HttpOnly 유지 — 별개.)
- **`ignoringRequestMatchers("/api/v1/analyses")`** — 공개 무상태 POST만 예외. **세션 인증을 안 쓰는 엔드포인트는 CSRF 공격 대상이 아니다**(공격자가 훔칠 세션이 없음). 이걸 빼야 익명 curl/외부 호출이 403 안 난다.
- **profile·logout 은 예외에 넣지 않는다** — 세션 인증 + 상태변경이라 반드시 토큰 필요. GET(`/laws`)은 CSRF 대상 아님(안전 메서드).

## 세션 정책 · 세션 vs JWT (D60)
- 서버 세션 + 세션 쿠키(**HttpOnly·SameSite=Lax**, `application.yml`). Secure는 미설정 → HTTPS 요청에서만 자동(로컬 HTTP 무해).
- **JWT 아닌 세션-쿠키 선택**: 같은 도메인 웹 UI에 최적 + **즉시 무효화**(로그아웃·파기 D41). JWT는 만료 전 폐기가 어렵다. 확장 시 `spring-session-jdbc`/Redis로 **저장소만** 교체(재설계 아님).
- **로그아웃**(POST `/logout`): 세션 무효화 + `JSESSIONID` 삭제 → D41 파기 요건.

## 부팅 게이팅 (구현 함정)
`oauth2Login()` 은 **`ClientRegistrationRepository` 빈이 있어야** 부팅된다. 이 빈은 client-id가 채워진 `oauth` 프로파일에서만 생성된다(키 없는 dev/CI에서 빈 client-id는 부팅을 깬다 → OAuth 설정을 `application-oauth.yml`로 분리한 이유).

→ `ObjectProvider<ClientRegistrationRepository>.ifAvailable(...)` 안에서만 `oauth2Login` 을 붙인다. **키 없는 기본/CI 부팅은 소셜 로그인 없이 깨끗하게 뜨고**, 키 있는 배포만 `--spring.profiles.active=oauth` 로 활성화.

## Error Handling
- 미인증 보호 엔드포인트 → **401**, 인가 실패 → **403**([[service-api-spec]] §4.1 정합). CSRF 실패 → 403.
- `oauth2Login` 구성 예외는 `IllegalStateException` 으로 승격(부팅 실패 = fail-fast).

## Design Constraints
- 규칙 순서 의존성(좁은 것 먼저) — 리팩터 시 `anyRequest()` 는 항상 마지막.
- 검증: 인가 규칙은 Spring Security 테스트(보호 401·공개 200)로 슬라이스 검증([[Auth]] §검증).
