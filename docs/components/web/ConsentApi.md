---
title: Consent API — 동의 REST 어댑터 (슬라이스 ②, web)
status: Draft
version: 0.1
date: 2026-09-27
tags: [component, web, rest, consent, privacy]
related: ["components/application/ProfileUseCase.md", "components/application/AccountUseCase.md", "components/web/ProfileApi.md", "components/auth/Auth.md", "mvp/service-api-spec.md", "adr/decision-log.md"]
---

# Consent API (web 계층, HTTP 어댑터)

> **동의라는 행위**를 프로필 수정과 분리한 엔드포인트(D61). 프로필 수집 동의와 알림 이메일 수신 동의를 받고, 두 동의의 상태를 보여준다. 규칙은 [[ProfileUseCase]]·[[AccountUseCase]]가 갖고 여기는 HTTP만.

## 왜 분리하나
동의는 한 번(처리방침 버전당) 하는 행위이고 프로필 수정은 여러 번 하는 행위다. 한 요청에 묶으면 ① 수정할 때마다 동의 체크를 요구하게 되고 ② 재동의하려면 프로필 전체를 다시 보내야 한다(`PUT /profile`이 전체 교체라서).

## Responsibility
- **담당**: 동의 요청 파싱 → 세션 `userId`로 유스케이스 위임 · 알림 동의 시 **세션 principal에서 IdP 이메일 추출**(`OAuth2Identity`) · 두 동의 상태 조합 응답.
- **담당 안 함**: 동의 규칙·버전 판정([[ProfileUseCase]]) · 이메일 필수 판정([[AccountUseCase]]) · 프로필 속성([[ProfileApi]]).

## HTTP Contract — `com.lia.core.web`
| 메서드 | 경로 | 동작 | 응답 |
|---|---|---|---|
| `GET` | `/api/v1/consents` | 두 동의 상태 | 200 |
| `PUT` | `/api/v1/consents/profile` | 바디 `{"over14": true}`. 프로필 수집 동의 — 행이 없으면 빈 프로필 생성, 있으면 버전·일시만 갱신. **재동의도 같은 호출** | 200 / 400 (`over14`≠true) |
| `PUT` | `/api/v1/consents/notification-email` | 알림 이메일 수신 동의 — 세션 principal의 IdP 이메일 저장 | 200 / 400 (IdP 이메일 없음) |
| `DELETE` | `/api/v1/consents/notification-email` | 알림 동의 철회 — email·동의 일시 null. 멱등 | 204 |

```jsonc
// GET /api/v1/consents — 동의 안 한 항목은 null
{ "profile": { "policyVersion": "draft-2026-09", "consentedAt": "…", "upToDate": true },
  "notificationEmail": { "email": "user@example.com", "consentedAt": "…" } }
// PUT /api/v1/consents/profile  → 200 { "policyVersion", "consentedAt", "upToDate": true }
// PUT /api/v1/consents/notification-email → 200 { "email", "consentedAt" }
```
- 프로필 동의 **철회는 `DELETE /api/v1/profile`**(= 파기, [[ProfileApi]]) — 동의 없는 프로필은 존재할 수 없으므로 철회와 파기가 같은 행위다.
- 인증 필수, 상태변경은 CSRF 토큰 필요.

## 구조 (컴포넌트)
| 클래스 | 역할 |
|---|---|
| `ConsentController` | `@RestController` `/api/v1/consents`. [[ProfileUseCase]]·[[AccountUseCase]] 둘 다 호출해 상태를 조합. |

- 이메일 추출은 로그인 핸들러와 **같은 provider 분기**(`auth.OAuth2Identity`)를 쓴다 — 분기가 두 곳에 생기지 않게([[OAuth2LoginSuccessHandler]]).
- principal이 OAuth2가 아니거나 IdP가 이메일을 주지 않았으면 이메일 null → [[AccountUseCase]]가 400.

## Error Handling
- `over14`≠true · IdP 이메일 없음 → 400. 미인증 → 401. 세션에 `userId` 없음 → 401.

## Side Effects
- 없음(위임).

## 검증
- 웹 슬라이스(MockMvc): 프로필 동의 → 상태 `upToDate=true` · `over14=false` 400 · 버전 상승 시 `upToDate=false` · 이메일 있는 principal 동의 → 저장·상태 반영 · 이메일 없는 principal 400 · 철회 204 후 상태 null.

## 의존 / 관련
[[ProfileUseCase]] · [[AccountUseCase]] · [[ProfileApi]] · [[Auth]] · [[component-specs]] §2(동의 모델) · D61.
