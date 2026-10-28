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

## 5. 1단계 구현 규약

| 항목 | 규약 | 이유 |
|---|---|---|
| 경로 | `/`(S01) · `/ask`(S02·S03 — 응답의 `resolution`으로 갈림) · `/info`(S15) · `not-found`·`error`(S16). 로그인 쪽(`/login`·`/login/done`·`/consent`·`/profile`·`/me`)은 같은 단계의 로그인 흐름에서 | S03은 별도 요청이 아니라 같은 응답의 상태 |
| 질문 보관 | 결과 화면으로 질문을 넘길 때 **화면 URL(`/ask?q=…`)에 싣지 않는다** — 탭 sessionStorage(`lia.ask`), 새로고침하면 같은 질문을 다시 보낸다(답변 캐시 D51). 분석 API 호출은 그대로 POST 본문 | 질문에 개인 사정이 섞일 수 있는데, 쿼리스트링은 접근 로그·브라우저 기록·Referer에 남는다(D50 로그 PII 규율, [[observability]] §4 ②). 탭 단위 유지는 [[screen-plan]] §0 |
| 데이터 조회 | 분석·데이터 현황·로그인 화면은 **브라우저 조회**(TanStack Query). 법령 화면(홈 목록·S05·S06)은 **서버 렌더링 조회** — Next 서버가 `LIA_API_ORIGIN`으로 직접 부르고(`lib/api/laws.ts`, `"use cache"` + `cacheLife("minutes")`), 조회는 `<Suspense>` 안 `await connection()` 뒤 | 빌드가 백엔드에 의존하지 않는다(요청 시점 조회). 적재는 하루 한 번이라 몇 분 캐시로 충분 |
| Next 기본값 | Cache Components·Partial Prefetching(16.x 기본) 유지 | 다음 메이저에서 필수 |
| 내비게이션 | 1단계는 **홈·안내** + 로그인 흐름의 **내 정보**. 알림 탭은 S11(4단계)과 함께 | 없는 화면으로 가는 탭을 두지 않는다 |
| 인용 칩 | 근거 패널(데스크톱)·아래 시트(모바일)에서 원문·현행 대비를 보이고, "법령 상세에서 보기"로 S05의 그 조문(`#art-6-3`)으로 간다. 결과 상단에도 "법령 상세 보기" | 조문 고유 주소 = `art-` + 조문번호(`의` → `-`) |
| 글꼴 | Pretendard(jsDelivr 동적 서브셋 CSS), 실패 시 시스템 글꼴 | [[visual-design]] §2 |
| 프록시 제한 시간 | `experimental.proxyTimeout` 120초 — 로컬 Next rewrites 기본 30초가 분석 요청보다 짧다 | Vercel 상한(120초)과 같게 |
| 환경변수 | `LIA_API_ORIGIN` — rewrites 목적지(로컬 기본 `http://localhost:8080`, 운영 `https://api.<도메인>`). **빌드 시점에 읽힌다**(rewrites 가 빌드 결과에 고정) — Vercel 은 Build 환경에도 설정해야 하고, 운영 빌드에서 비어 있으면 빌드를 실패시킨다 | §2 |
| 로그인 복귀 | 로그인을 시작하는 곳이 돌아올 경로를 탭 sessionStorage(`lia.returnTo`, 같은 출처 경로만)에 둔다. 백엔드가 보내는 `/login/done`이 동의 상태를 보고 `/consent`(동의 없음·옛 버전) 또는 그 경로로 보낸다. 첫 동의 뒤엔 `/profile`을 거친다 | [[screen-plan]] S07 "원래 화면으로". OAuth 왕복에도 같은 탭의 sessionStorage는 남는다 |
| 로그인 상태 | `GET /api/v1/account` — 401이면 익명. 보호 화면(`/consent`·`/profile`·`/me`)은 익명이면 `/login`으로, 작업 중 401은 "다시 로그인" 대화상자(S16) | 계정 API가 상태 확인 겸용([[AccountApi]]) |
| 재동의 | 프로필 저장이 409면 화면을 떠나지 않고 대화상자에서 동의 후 저장을 다시 시도. 재동의도 만 14세 확인을 함께 받는다(API가 매번 요구) | 입력한 값을 잃지 않게 |
| 프로필 선택지 | `web/src/lib/profile-options.json` 한 곳 — 백엔드 `ProfileOptionsSyncTest`가 enum 라벨과 같은지 확인 | [[screen-plan]] S09 "프런트 상수, enum 변경 시 함께" |
| 내 영향 안내 | PersonalizeAction 은 미충족 사유별 행동 — `PROFILE_REQUIRED`: 익명 → "로그인하기"(`/login`), 로그인 → "프로필 입력하기"(`/profile`, 빈 프로필 포함) <br> `CONSENT_OUTDATED`: "동의하기"(`/consent`) <br> 그 외 사유는 버튼 없이 문구만(`unmetCopy`) | 지키지 못할 약속을 하지 않는다 |
| 알림 이메일 | 내 정보의 스위치는 수신 동의 저장만 — 발송은 S11(4단계). 화면에 "알림 기능은 곧 제공돼요" | — |
| 법령 화면 경로 | `/laws`(S06 곧 시행, `?range=week·month·quarter` = 오늘부터 7·30·90일, 기본 month) · `/laws/[lawId]`(가장 이른 시행예정본으로 이동) · `/laws/[lawId]/[efYd]`(S05). 홈 질문창 아래 곧 시행 5개 | [[screen-plan]] S01·S05·S06 |
| 없는 법령·이동 | 상세·이동 라우트는 `<Suspense>` 안에서 조회하므로 응답이 이미 흐르기 시작한 뒤 `notFound()`·`redirect()`가 결정된다 — HTTP 상태는 200(운영 빌드도 같음). not-found 화면엔 Next가 `<meta name="robots" content="noindex">`를 자동으로 붙여 검색에 남지 않고, 이동은 meta refresh 로 된다 | Cache Components 스트리밍의 성질. 실제 404 가 필요해지면 조회를 Suspense 밖(정적 매개변수)으로 옮겨야 한다 |
| 법령 상세 요약 | 미리 만든 **쉬운 요약**(D67)을 기본 표시 — 문단·주요 내용(인용 칩: 바뀐 조문이면 같은 페이지 `#art-N`)·불확실한 점·"근거 조문으로 만든 AI 요약" 안내, 개정 이유 원문은 접힘. 요약이 없으면 "쉬운 요약 보기"(`lia.ask` ← `{query: "{법령명} 쉬운 요약", lawRef}` → `/ask`). 시행예정본에서만 | 원래 화면 계획 — 쉬운 설명이 기본, 원문은 필요할 때 |
| 빈 자리 | "내 관련" 탭·북마크·시행 알림 받기·분야 필터는 해당 기능과 함께 붙인다. 자주 찾는 목록은 홈에선 집계가 없으면 숨기고, S06 탭은 구조라 남겨 "아직 집계된 조회가 없어요" | 지키지 못할 약속을 하지 않는다 |
| 자주 찾는 법령 | 홈은 곧 시행 옆(데스크톱 2단) 이번 주 상위 5개, S06은 `?tab=trending&window=day·week·month` 상위 20개. 변화 표시는 `previousRank`로 ▲n·▼n·–·NEW(글자 병기). S05는 시행예정본일 때 조회 신호를 탭당 법령별 1회(sessionStorage `lia.viewed`) | D66 |
| E2E 모의 API | 서버 컴포넌트 조회는 브라우저 밖이라 `page.route`로 못 가로챈다 — Playwright가 `e2e/mock-api/server.mjs`를 함께 띄우고 Next를 `LIA_API_ORIGIN=모의 서버`로 빌드·기동 | 브라우저 조회는 기존처럼 `page.route` |

## 6. 다음
- 시각 디자인·화면 시안 — [[visual-design]](방향 A 신뢰·공공형, 토큰, 레이아웃 규칙).
- 구현 계획 — [[screen-plan]] §6의 4단계를 따른다.
