---
title: Auth Providers API — 켜진 소셜 로그인 목록 (web 계층)
status: Draft
version: 0.1
date: 2026-11-02
tags: [component, web, rest, auth]
related: ["mvp/service-api-spec.md", "components/auth/Auth.md", "components/auth/SecurityConfig.md", "product/screen-plan.md", "backend/cloud-infra.md"]
---

# Auth Providers API

> `GET /api/v1/auth/providers` — 지금 켜진 소셜 로그인 provider 목록. 웹 로그인 화면(S07)이 이 목록의 버튼만 그린다(D78). 계약: [[service-api-spec]] §3.4.

## 왜
- 로컬·운영 모두 키는 구글만 있다. 세 provider 를 무조건 등록하면 키 없는 provider 때문에 부팅이 깨진다 → **`client-id` 가 있는 provider 만 등록**한다([[Auth]]).
- 그러면 화면도 켜진 provider 만 보여야 한다. 웹 빌드 환경변수로 따로 정하면 백엔드 설정과 어긋날 수 있어, **켜진 목록의 단일 소스는 백엔드 설정**이다.

## 구조
| 클래스 | 역할 |
|---|---|
| `AuthProvidersController` | `GET /api/v1/auth/providers` → `{ "providers": [...] }` 200. 익명 공개, 세션을 읽지 않는다. |
| `EnabledOAuthProviders` | 등록된 provider id 목록 — oauth 프로파일의 등록 결과(`ClientRegistrationRepository`)에서 만든다. 프로파일이 꺼져 있거나 등록이 없으면 빈 목록. 순서는 `google`·`kakao`·`naver` 고정. |

## 계약
```jsonc
// GET /api/v1/auth/providers → 200
{ "providers": ["google"] }      // client-id 가 설정된 provider 만. 없으면 []
```

## Invariants
- 목록은 **실제로 로그인할 수 있는 provider 와 정확히 같다** — 등록과 같은 소스에서 만든다(목록엔 있는데 로그인이 404 인 경우가 없다).
- id·순서만 공개한다. client-id·리다이렉트 주소 같은 설정값은 내보내지 않는다.

## 검증
- 등록 단위: `client-id` 가 빈 provider 는 등록되지 않고, 전부 비면 등록 없음(로그인 비활성 — 부팅은 성공).
- 웹 슬라이스: 익명 200 · 키가 구글만 있을 때 `["google"]` · oauth 프로파일 꺼짐 → `[]`.
- 웹: S07 이 목록의 버튼만 그린다 · 빈 목록이면 준비 중 안내 · 실패 시 오류 안내.

## 의존 / 관련
[[Auth]] · [[SecurityConfig]](공개 경로) · [[screen-plan]] S07 · D78.
