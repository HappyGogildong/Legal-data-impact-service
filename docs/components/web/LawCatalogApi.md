---
title: Law Catalog API — 법령 목록·상세 (web 계층)
status: Draft
version: 0.1
date: 2026-10-25
tags: [component, web, rest]
related: ["mvp/service-api-spec.md", "product/screen-plan.md", "components/web/AnalysisApi.md", "components/batch/IngestJob.md"]
---

# Law Catalog API

> `GET /api/v1/laws` · `GET /api/v1/laws/{lawId}` · `GET /api/v1/laws/{lawId}/{efYd}` — 홈 "곧 시행되는 법령"·S06 목록·S05 상세. 계약: [[service-api-spec]] §3.1~3.3, 결정: D65.

## 구조
| 클래스 | 역할 |
|---|---|
| `LawController` | HTTP만 — 파라미터 검증(400)·없음(404, `LawNotFoundException`). 익명 공개(`SecurityConfig`의 `/api/v1/laws/**`). |
| `LawCatalogStore` | `law_versions` 읽기 전용 — 목록(컬럼 + `payload->>` 필드만, payload 역직렬화 없음, 키셋 커서)·시행예정본 목록. 오늘 = `CAST(now() AT TIME ZONE 'Asia/Seoul' AS date)`. |
| `LawDetailMapper` | `Law`(+기준선) → `LawDetailResponse` 표현. 조문 대비는 `ArticleComparison`(근거 패널 `CitedSourceResolver`와 공용). |
| 상세 조회 | 기존 `LawSource.find`·`findBaseline` — 기준선은 시행예정본일 때만 읽는다. |

## Invariants
- **결정론** — LLM을 부르지 않는다. 요약은 화면 버튼이 `POST /analyses`로.
- 목록은 **시행예정 · 오늘(서울) 이후**만. 시행일이 지났는데 아직 전환 배치가 안 돈 행은 목록에서 빠지지만 상세는 열린다.
- 상세는 **상태 무관**(시행예정·시행중·과거·철회) — 예전 인용이 깨지지 않게(D62 행 보존).
- `previousText`는 시행예정본의 개정·삭제·이동 조문만 — 시행된 버전은 기준선이 자기 자신이라 대비하지 않는다.
- 커서는 불투명 — `(effectiveDate, lawId)`의 base64url. 클라이언트는 해석하지 않는다.

## 인덱스
- V6 `law_versions (status, effective_date, law_id)` — 목록 조건·정렬·키셋을 한 인덱스로.

## 검증
- `LawCatalogStoreIntegrationTest`(Testcontainers) — 오늘 이후 시행예정만·정렬, 기간 범위, 커서 쪽 넘김(같은 시행일 lawId 순), 시행예정본·기준선, 없는 법령.
- `LawCatalogCursorTest` — 커서 왕복·잘못된 커서.
- `LawDetailMapperTest` — 시행예정본 대비, 시행된 버전·기준선 없음.
- `LawApiTest`(웹 슬라이스) — 익명 200, 400(형식·범위·커서·limit), 404.
