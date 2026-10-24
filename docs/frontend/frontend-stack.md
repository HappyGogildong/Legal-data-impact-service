---
title: 웹 프런트엔드 기술 스택 — Next.js on Vercel
status: Draft
version: 0.1
date: 2026-10-23
tags: [frontend, web, nextjs, vercel, stack]
related: ["product/screen-plan.md", "backend/cloud-infra.md", "mvp/service-api-spec.md", "components/auth/SecurityConfig.md", "components/web/AnalysisApi.md", "adr/decision-log.md"]
---

# 웹 프런트엔드 기술 스택

> [[screen-plan|화면 계획]](S01~S16)을 구현할 기술과 배포 방식. 근거: [[decision-log]] D64. 백엔드·인프라: [[cloud-infra]](D63).

## 1. 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| 프레임워크 | **Next.js (App Router) + TypeScript** | Vercel 배포 |
| 호스팅 | **Vercel**(Hobby — 개인·비상업 용도) | 무료 CDN · PR 미리보기 배포 · EC2 메모리 미사용 |
| 렌더링 | 공개 페이지(S01 목록·S05·S06·S15)는 **서버 렌더링/정적 재생성**, 개인화 화면(S02 개인화 부분·S07~S14)은 **브라우저 렌더링** | 법령 상세가 검색에 노출, 개인화는 세션 쿠키 그대로 |
| 백엔드 연결 | **Vercel rewrites** — `/api/*`·`/oauth2/*`·`/login/oauth2/*`·`/logout` → EC2 | 브라우저 입장에서 출처가 하나(§2). 서버 렌더링의 공개 데이터 조회는 Next 서버가 EC2 API를 직접 호출 |
| 스타일·부품 | **Tailwind CSS + shadcn/ui**(Radix 기반) | 시트·다이얼로그·탭·칩 등 — 부품 코드를 레포에 두고 고친다 |
| 데이터 조회 | 서버: Next 서버 컴포넌트 `fetch` · 브라우저: **TanStack Query** | 분석 대기 상태·재시도·캐시·상황 확인 후 대응 방안만 다시 불러오기 |
| API 타입 | 백엔드 **typed DTO + springdoc(OpenAPI)** → **openapi-typescript**로 `web/` 타입 생성 | 계약의 단일 소스는 백엔드 코드. 어긋나면 CI 타입 검사 실패 |
| 레포 구조 | **같은 레포의 `web/`** | 문서·타입 생성·PR이 한 곳. Vercel 루트 디렉터리 = `web/` |
| 패키지 관리 | **pnpm** | — |
| 테스트 | 단위 **Vitest + Testing Library** · 주요 흐름 E2E **Playwright**(익명 질문·해소 안내·로그인 유도 흐름, 모의 API) | — |
| 품질 | ESLint + Prettier · CI `web` 작업(타입 검사·린트·테스트·빌드) | — |
| 접근성 | Radix 기본 + 키보드 탐색·명도 대비·스크린리더 라벨 점검 | 공공 성격 서비스라 기본값 |

## 2. 인증·CSRF — rewrites로 출처를 하나로

현재 인증은 **소셜 OAuth2 + 서버 세션 쿠키 + CSRF 토큰(`csrf.spa()`)**이고 OAuth 리다이렉트도 한 도메인 기준이다(D60). 프런트(Vercel)와 API(EC2)를 다른 출처로 두면 CORS·쿠키 도메인·CSRF 토큰 전달(프런트 JS는 API 도메인의 쿠키를 못 읽는다)을 모두 바꿔야 한다.

**Vercel rewrites**로 API·로그인 경로를 프런트 도메인 아래로 전달하면 브라우저에는 출처가 하나다:

```
브라우저 ──▶ app.<도메인> (Vercel)
               ├ 화면(Next.js)
               └ /api/* · /oauth2/* · /login/oauth2/* · /logout ──rewrite──▶ api.<도메인> (EC2 · Caddy · core)
```

- 세션 쿠키는 프런트 도메인의 퍼스트파티 쿠키 — 백엔드 `SecurityConfig` 변경 없음.
- CSRF — 프런트가 `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 보낸다(같은 출처라 읽을 수 있음).
- **OAuth 리다이렉트 주소를 프런트 도메인으로 명시** — `spring.security.oauth2.client.registration.<provider>.redirect-uri=https://app.<도메인>/login/oauth2/code/{registrationId}`. 콜백이 rewrites로 EC2에 전달된다. provider 콘솔에도 이 주소를 등록.
- `FRONTEND_URL`(로그인 성공·실패 후 이동) = `https://app.<도메인>`.
- **제약**
  - 프록시 요청 제한 시간 120초(전 플랜 동일) — 분석 요청(수십 초) 범위 안.
  - 요청이 Vercel을 한 번 거친다(서울 엣지).
  - PR 미리보기 배포는 OAuth 리다이렉트 도메인과 달라 로그인 테스트가 안 된다 — 미리보기는 익명 화면 확인용.

## 3. 백엔드에 생기는 변경

| 변경 | 이유 |
|---|---|
| 분석 응답 `Map` → **typed DTO**(`AnalysisApiResponse`) + 법령 헤더·다른 시행일·근거 원문(`sources`, 현행 원문 포함) · 미충족 사유 코드(`unmet.<차원>.reason`) | 응답 계약을 타입으로 고정, 결과 화면(S02)의 법령명·시행일 선택·근거 패널·조문 대비·사유별 안내 카드([[AnalysisApi]]) |
| **springdoc** `/v3/api-docs` + 레포 `api/openapi.json` 스냅샷 테스트 | TS 타입 생성의 원천, 계약이 바뀌면 테스트가 드러냄. 운영은 `SPRINGDOC_API_DOCS_ENABLED=false` |
| **데이터 현황 API** `GET /api/v1/data-status` | S15·S05의 갱신 시각·수록 법령 수([[DataStatusApi]]) |
| OAuth `redirect-uri` = `${OAUTH2_REDIRECT_BASE_URL:{baseUrl}}/…` · `FRONTEND_URL` = 프런트 도메인 · 로그인 착지 `/login/done`·실패 `/login?error=oauth` | §2 — 운영은 `OAUTH2_REDIRECT_BASE_URL=https://app.<도메인>` |

## 4. 도메인

| 호스트 | 대상 |
|---|---|
| `app.<도메인>` | Vercel(웹) |
| `api.<도메인>` | EC2 Elastic IP(Caddy → core) — rewrites의 목적지 |

무료 서브도메인을 쓰면 웹은 Vercel 기본 도메인(`*.vercel.app`)도 가능하다 — 그때 OAuth 리다이렉트·`FRONTEND_URL`을 그 도메인으로.

## 5. 다음
- 시각 디자인·화면 시안 — [[visual-design]](방향 A 신뢰·공공형, 토큰, 레이아웃 규칙).
- 구현 계획 — [[screen-plan]] §6의 4단계를 따른다.
