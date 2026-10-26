---
title: Trending API — 자주 찾는 법령 (web 계층)
status: Draft
version: 0.1
date: 2026-10-26
tags: [component, web, rest]
related: ["mvp/service-api-spec.md", "product/screen-plan.md", "components/web/LawCatalogApi.md", "components/auth/SecurityConfig.md"]
---

# Trending API

> `GET /api/v1/laws/trending` · `POST /api/v1/laws/{lawId}/{efYd}/views` — 홈 "자주 찾는 법령"·S06 "자주 찾는" 탭. 계약: [[service-api-spec]] §3.5, 결정: D66.

## 구조
| 클래스 | 역할 |
|---|---|
| `LawViewController` | HTTP만 — `window`·`limit`·날짜 검증(400), 없는 버전(404), 조회 신호 204. 익명 공개, 조회 신호는 CSRF 예외. |
| `LawViewStore` | `law_views_daily` — `record`(서울 오늘 행 upsert 증가, 버전이 없으면 false)·`trending`(구간 합산 순위 + 직전 같은 길이 구간 순위, 시행예정·오늘 이후만). `LawViews` 포트 구현. |
| `TrendingWindow` | day(1일)·week(7일)·month(30일). 이름 파싱(그 밖은 `IllegalArgumentException`). |
| `AnalyzeUseCase` | Reference 분석이 정본으로 답을 냈을 때 `LawViews.record` — 실패는 삼키고 로그. |

## Invariants
- **횟수만** — 테이블에 사람을 가리키는 열이 없다(D41).
- 같은 서울 날짜·같은 버전은 한 행. 증가는 한 문장 upsert라 동시 요청에도 잃지 않는다(행 잠금).
- 순위는 시행예정·오늘 이후 버전만 — 시행된·철회된 법령은 오르지 않는다.
- 분석 기록 실패가 분석 응답을 바꾸지 않는다.

## 검증
- `LawViewStoreIntegrationTest`(Testcontainers) — 증가·없는 버전 false·구간 합산·직전 순위·필터·동점.
- `TrendingWindowTest` — 파싱.
- `AnalyzeUseCaseTest` — 답을 낸 Reference 분석만 기록, 기록 실패에도 결과 그대로.
- `LawViewApiTest`(웹 슬라이스) — 200·204·400·404. `SecurityConfigTest` — 익명 조회 신호가 CSRF 토큰 없이 통과.
