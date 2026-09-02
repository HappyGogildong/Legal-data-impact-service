---
title: IngestService — 클래스 스펙
status: Draft
date: 2026-08-27
tags: [component, service, orchestrator, ingest, pipeline]
related: ["components/component-specs.md", "components/ingest/LawConnector.md", "components/ingest/Normalizer.md", "components/ingest/DiffBuilder.md", "components/store/LawStore.md", "reference/law-domain-basics.md", "adr/decision-log.md"]
---

# IngestService

> 오프라인 **적재 오케스트레이터**(D40) — 흩어진 단계를 하나의 배치로 엮는다: `LawConnector fetch → Normalizer → DiffBuilder → LawStore upsert`. 스스로 변환·저장 로직을 갖지 않고 **순서·경계·계측**만 책임진다. 스키마 SSOT: [[component-specs]] §1.

## Responsibility
- **담당:** 적재 단계들의 **조립·순서 강제**(정규화→대조→저장) · fetch 오케스트레이션(목록→본문→기준선) · 시행예정본과 (있으면)시행중 기준선본을 **함께** 저장 · `lia.ingest` 계측(span/timer).
- **담당 안 함:** 출처 호출 형식([[LawConnector]]) · 조문 병합·부칙 필터([[Normalizer]]) · 신설/삭제/이동 판정([[DiffBuilder]]) · 영속·멱등([[LawStore]]) · 임베딩·색인([[RAGIndexer]]). **오케스트레이터는 로직을 위임만 한다.**

## Collaborators
- **[[LawConnector]]** — `listPending` · `fetchPending(mst, efYd)` · `fetchCurrent(lawId)`(제정이면 null). `store(...)` 경로에서는 쓰지 않음(주입은 받되 미사용 — 테스트는 null 주입).
- **[[Normalizer]]** · **[[DiffBuilder]]** · **[[LawStore]]** — 순수 위임.
- **ObservationRegistry** — 계측(null이면 `NOOP`).
- 외부 시스템: 국가법령정보 API(배치 경로만) · Postgres(Store 경유).

## Business Flow
두 진입점 — **조립 단위**와 **배치**를 분리해 조립 로직을 API 없이 테스트 가능하게 둔다.

- **`store(pendingRaw, baselineRaw)` — 조립 단위(API 불필요).**
  1. `pending = normalize(pendingRaw)`, `baseline = baselineRaw==null ? null : normalize(baselineRaw)`.
  2. `diffed = diffBuilder.build(pending, baseline)` — 기준선 null(제정)이면 변경 조문 전부 `신설`.
  3. `lawStore.upsert(diffed)` — 시행예정 정본(diff 포함).
  4. `baseline != null`이면 `lawStore.upsert(baseline)` — 시행중 정본(= diff 기준선)도 저장 → 이후 `findBaseline`으로 조회 가능.
  5. `IngestResult(lawId, effectiveDate, changedCount, hasBaseline)` 반환.
- **`ingestPending(from, to, limit) → IngestSummary` — 배치(API 필요).**
  - `listPending` top-`limit` → 각 head: `fetchPending(mst, efYd)` + `fetchCurrent(lawId)`(null=제정) → `store` → `[ingest]` 로그. 요약 `IngestSummary(listed, stored, withBaseline)`.

## Transaction Boundary
- **정본 저장은 upsert 멱등**([[LawStore]] `ON CONFLICT`)이라 **명시적 트랜잭션을 두지 않는다** — 배치 재실행이 안전(재적재 = 덮어쓰기).
- 경계 단위 = **법령 1건**(pending + 그 기준선). 한 건 실패가 다른 건을 오염시키지 않도록 건 단위로 처리(배치 부분 실패 정책은 [[#Design Constraints]]).
- `store()` 안의 두 upsert(시행예정·시행중)는 원자적 묶음이 **아니다** — 멱등이라 재실행으로 수렴하므로 분산 트랜잭션 불필요(D54 SSOT 모델).

## Business Rules
- **제정 법령 = 기준선 없음** — `fetchCurrent`가 null(현행본 부재, [[law-domain-basics]] §3) → `DiffBuilder(baseline=null)` → 전부 신설, 시행중 정본 미저장 → `findBaseline` empty. 이것은 오류가 아니라 정상 경로.
- **기준선본도 저장한다** — diff의 기준이 된 시행중본을 그냥 버리지 않고 함께 upsert해, 이후 `findBaseline`·정확 조회·(후속)context 조립에서 재사용.
- 정본 단위 = **`(lawId, effectiveDate)`**(D43) — 조립도 이 단위로 결과를 낸다.

## Invariants
- 저장되는 시행예정 정본은 **항상 diff를 거친 것**(`diffBuilder.build` 산출) — raw 정규화본을 바로 저장하지 않는다.
- `IngestResult.hasBaseline == (baselineRaw != null)` — 제정/개정 판별의 단일 근거.

## Error Handling
- 단계 위임 중 예외는 **감싸지 않고 전파**(fail-fast) — 배치 상위(스케줄러/CLI)가 재시도. 오케스트레이터는 예외를 삼키지 않는다.
- `Observation`으로 감싸 실패도 계측에 error로 기록된다.

## Side Effects
- **DB 쓰기**([[LawStore]] upsert) · **외부 API 호출**(배치 경로 `ingestPending`만; `store`는 없음) · 로그·메트릭.

## Design Constraints
- **오프라인/배치 전용**(D40) — 요청 경로(온라인)에서 호출하지 않는다.
- `@Component`지만 요청 스코프 빈이 아니며, DB 없는 스프링 컨텍스트에서도 빈 생성은 성립해야 한다(카나리아 `CredentialLoadingTest`로 회귀 감시).
- 배치 부분 실패 정책(한 건 실패 시 계속 vs 중단)·재시도·병렬도는 **미확정** — 현재는 fail-fast. 확정 시 [[decision-log]]에 기록.

## 실측 검증
- 실 Postgres 조립 통합테스트 2건(Testcontainers, `IngestServiceIntegrationTest`):
  - **기준선 있음** — 주택법 pending↔baseline → 제18조 `개정`·`diffVsCurrent` 저장, `findBaseline` 존재.
  - **제정(baseline=null)** — 변경 조문 전부 `신설`, `findBaseline` empty.
