# LIA 웹

입법 영향 분석기(LIA)의 웹 화면 — Next.js 16(App Router) · React 19 · Tailwind · TanStack Query. 화면·스택 결정은 [`docs/frontend/frontend-stack.md`](../docs/frontend/frontend-stack.md)를 본다.

## 준비

- Node 24, pnpm 12 (`package.json`의 `packageManager` 버전)
- `pnpm install`

## 명령

| 명령             | 하는 일                                                                                                        |
| ---------------- | -------------------------------------------------------------------------------------------------------------- |
| `pnpm dev`       | 개발 서버(http://localhost:3000)                                                                               |
| `pnpm test`      | 단위 테스트(Vitest)                                                                                            |
| `pnpm e2e`       | 익명·법령 흐름 E2E(Playwright) — 서버 렌더링 조회용 모의 API(`e2e/mock-api/server.mjs`, 포트 3199)도 함께 띄움 |
| `pnpm gen:api`   | `api/openapi.json` → API 타입 생성                                                                             |
| `pnpm typecheck` | 라우트 타입 생성 + `tsc --noEmit`                                                                              |
| `pnpm lint`      | ESLint                                                                                                         |
| `pnpm format`    | Prettier                                                                                                       |

## 로컬 백엔드

`/api`·`/oauth2`·`/login/oauth2`·`/logout` 요청은 Next rewrites 로 백엔드(core)에 넘긴다. 기본 목적지는 `http://localhost:8080`이고, 바꾸려면 `LIA_API_ORIGIN`을 설정한다(`.env.example` 참고 — 빈 값·끝 `/` 없이).

## API 타입

타입은 저장소 루트의 `api/openapi.json`에서 생성한다(`pnpm gen:api` → `src/lib/api/schema.d.ts`). 백엔드 API 가 바뀌면 스냅샷과 생성 결과를 함께 커밋하고, CI 가 둘이 어긋났는지 검사한다.

## Vercel 배포 메모

- 프로젝트 루트 디렉터리는 `web/`.
- `LIA_API_ORIGIN`은 **빌드 시점 변수**(rewrites 가 빌드 결과에 고정)라 Build 환경에도 설정해야 한다. 운영 빌드에서 비어 있으면 빌드가 실패한다.
- pnpm 버전이 맞지 않으면 환경변수 `ENABLE_EXPERIMENTAL_COREPACK=1`을 둔다.
