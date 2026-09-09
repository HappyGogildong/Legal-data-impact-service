---
title: Auth — 인증·계정 (소셜 OAuth2 + 세션, 슬라이스 ①)
status: Draft
version: 0.1
date: 2026-09-09
tags: [component, auth, security, oauth2, session]
related: ["components/component-specs.md", "components/profile/UserProfile.md", "components/web/ProfileApi.md", "mvp/service-api-spec.md", "adr/decision-log.md"]
---

# Auth (Spring Security, 소셜 OAuth2 + 서버 세션)

> UserProfile(#12)의 전제인 **신원/계정 기반**. 소셜 OAuth2 로그인으로 사용자를 식별하고 **서버 세션(HttpOnly 쿠키)**으로 상태를 유지한다. 최소 PII(D41) — 비밀번호·성명은 저장하지 않는다. **이메일은 알림 채널로 저장**(있을 때·동의 기반, 알림 기능 예정). 관련: [[UserProfile]] · [[ProfileApi]] · [[service-api-spec]] §3.4.

## 설계 결정 (D60 예정)
- **소셜 OAuth2**(Kakao/Naver/Google) — 자격증명·비밀번호 책임을 IdP에 위임(최소 PII·D41 정합, 한국 시민 서비스 관례).
- **세션-쿠키**(서버 세션) — 같은 도메인 웹 UI(#14)에 최적, **즉시 무효화**(로그아웃·파기 D41), HttpOnly로 XSS 기본 방어. 확장 시 Spring Session(JDBC/Redis)으로 저장소만 교체.

## Responsibility
- **담당**: OAuth2 로그인 흐름·서버 세션 수립, provider 신원 → 내부 `userId` 매핑, 계정 영속, 인가 규칙(엔드포인트 보호), 로그아웃·파기.
- **담당 안 함**: 프로필 속성 저장([[UserProfile]]) · 프로필 API([[ProfileApi]]) · 분석(application/pipeline).

## Collaborators
- **Spring Security** (`spring-boot-starter-security` · `spring-boot-starter-oauth2-client` — 신규 의존).
- **IdP**: Kakao/Naver/Google OAuth2 (client-id/secret는 `.env`, D39).
- **DB**: `accounts` 테이블(JdbcClient+Flyway, [[LawStore]] 패턴).
- 소비자: [[ProfileApi]]·Layer B 경로가 세션 principal의 `userId`를 읽는다.

## 구조 (컴포넌트) — `com.lia.core.auth`
| 클래스 | 역할 |
|---|---|
| `SecurityConfig` | `@EnableWebSecurity` — `SecurityFilterChain`: OAuth2 Login, 세션 정책, 인가 규칙(아래), CSRF, 로그아웃. |
| `Account` (record) | `userId(UUID)` · `provider` · `providerId`(opaque subject) · `email?`(알림 채널, IdP 제공 시) · `createdAt`. **성명·비밀번호 없음.** |
| `AccountStore` | JdbcClient — `findByProvider(provider, providerId)` · `create(...)` · `delete(userId)`. 테이블 `accounts`. |
| `OAuth2LoginSuccessHandler` (또는 `OidcUserService`) | 로그인 성공 시 `provider+subject` → `AccountStore` 조회/생성 → 세션 principal에 `userId` 부여. |
| `CurrentUser` | 세션 principal → `userId` 추출 헬퍼(컨트롤러용). |

## 인가 규칙 (SecurityFilterChain)
| 경로 | 정책 |
|---|---|
| `/oauth2/**`·`/login/**`·`/logout` | 공개(로그인 흐름) |
| `/api/v1/laws/**` · `POST /api/v1/analyses` | 공개(익명 Layer A 허용 — 프로필 없으면 Layer B는 `unmet`) |
| `/api/v1/profile/**` | **인증 필수** |
| `/actuator/health`·`/prometheus` | 공개(관측) |
| 그 외 상태변경 | **CSRF 토큰** 필요(쿠키 인증) |

## Persistence Contract
- `accounts(user_id uuid PK, provider text, provider_id text, email text NULL, created_at timestamptz, UNIQUE(provider, provider_id))` — Flyway `V2__accounts.sql`. `email`은 알림 발송용(nullable — Kakao 등 미제공 가능).
- `findByProvider` 로 재로그인 시 기존 `userId` 회수(멱등). `delete(userId)` 는 파기(프로필도 함께, [[UserProfile]] cascade/명시 삭제).

## Invariants
- **최소 수집**: `provider`+opaque `providerId`+`userId`(+알림용 `email`). IdP가 준 **이름은 저장하지 않는다**(D41). 이메일은 알림 채널로만 보관(D41의 연락처 미수집을 알림 목적에 한해 수정 — 동의·파기 대상).
- `userId` 는 버전 불변 내부 UUID(프로필·캐시·로그의 계정 키). 프롬프트엔 절대 주입 안 함(D41·D10).
- 한 (provider, providerId) → 정확히 하나의 `userId`.

## Session / Cookie
- 서버 세션 + 세션 쿠키 **HttpOnly · Secure · SameSite=Lax**. 로그아웃/파기 시 세션 즉시 무효화.
- 확장 시 `spring-session-jdbc`(또는 Redis)로 저장소만 교체 — 재설계 아님.

## Error Handling
- 미인증 보호 엔드포인트 → **401**(시스템 오류만 4xx, [[service-api-spec]] §4.1과 정합 — 인증은 401/403). CSRF 실패 → 403.
- OAuth 콜백 실패 → 로그인 페이지 리다이렉트(안내).

## Side Effects
- **DB 쓰기**(accounts upsert on first login) · 세션 생성/삭제. IdP 호출(로그인 시).

## 프라이버시 (D41, 횡단)
- 가입(최초 로그인) 시 **수집 동의 + 개인정보처리방침** 게이트. `DELETE` 계정=파기(세션 무효화 + accounts·user_profiles 삭제).
- IdP 반환 **이름은 버린다**(미저장). 이메일은 알림 동의 시에만 저장, `DELETE` 파기 대상.

## 검증
- 단위: `AccountStore`(Testcontainers — findByProvider 멱등·delete) · `OAuth2LoginSuccessHandler`(provider+subject→userId 생성/회수, Fake AccountStore).
- 슬라이스: 인가 규칙(보호 엔드포인트 401, 공개 200) — Spring Security 테스트.
- 라이브(수동): 실제 소셜 로그인 왕복.

## 의존 / 관련
[[UserProfile]](② store) · [[ProfileApi]](② HTTP) · Layer B(③ userId→프로필→프롬프트) · [[service-api-spec]] §3.4 · [[component-specs]] §2. 신규 의존: `spring-boot-starter-security`·`spring-boot-starter-oauth2-client`.
