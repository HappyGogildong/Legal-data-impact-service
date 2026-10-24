---
title: Data Status API — 데이터 현황 (web 계층)
status: Draft
version: 0.1
date: 2026-10-24
tags: [component, web, rest, ops]
related: ["mvp/service-api-spec.md", "components/batch/IngestJob.md", "product/screen-plan.md", "backend/cloud-infra.md"]
---

# Data Status API

> `GET /api/v1/data-status` — 법령 정보가 **얼마나 최신인지**를 사용자에게 보여 준다(화면 S15 안내·S05 하단). 계약: [[service-api-spec]] §3.7.

## 구조
| 클래스 | 역할 |
|---|---|
| `DataStatusController` | `GET /api/v1/data-status` → `DataStatusStore.read()` 그대로 200. 익명 공개. |
| `DataStatusStore` | `JdbcClient` 읽기 두 개 — 갱신 시각·시행예정 수. |

## 정의
- **`lawsUpdatedAt`** — `ingest_runs` 중 `command IN ('run', 'run --full')` · `outcome IN ('SUCCESS', 'PARTIAL')` · `list_complete = true`인 실행의 `max(finished_at)`. 목록 전체를 받아 갱신을 마친 마지막 시각이다(`--dry-run`·단건 `sync`·`index` 제외). 없으면 `null`.
- **`pendingLawCount`** — `law_versions`의 `status = '시행예정'` 행 수.

## Invariants
- **시각·개수만** 공개한다. 격리 작업 수·오류 내용 같은 운영 세부는 Grafana([[cloud-infra]] §4) — 공개 API가 운영 정보의 뒷문이 되지 않게.

## 검증
- `DataStatusStoreIntegrationTest`(Testcontainers) — 빈 DB(null·0), 시행예정만 셈, 갱신 시각 정의(dry-run·미완주·FAILED 제외).
- `DataStatusApiTest`(웹 슬라이스) — 익명 200·필드.

## 의존 / 관련
[[IngestJob]](`ingest_runs`) · [[LawStore]](`law_versions`) · [[screen-plan]] S15.
