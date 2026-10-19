---
title: IngestService — 클래스 스펙
status: Draft
date: 2026-10-13
tags: [component, service, orchestrator, ingest, pipeline]
related: ["components/batch/IngestJob.md", "components/component-specs.md", "components/normalize/Normalizer.md", "components/diff/DiffBuilder.md", "components/store/LawStore.md", "reference/law-domain-basics.md", "adr/decision-log.md"]
---

# IngestService

> 법령 한 건의 **정본 조립**: `Normalizer → DiffBuilder → LawStore upsert`. 스스로 변환·저장 로직을 갖지 않고 **순서·경계·계측**만 책임진다. 배치 실행·fetch·재시도는 [[IngestJob]](D62)이 맡고, 이 클래스는 그 `SYNC` 작업이 호출하는 조립 단위다. 스키마 SSOT: [[component-specs]] §1.

## Responsibility
- **담당:** 조립 단계의 **순서 강제**(정규화→대조→저장) · 시행예정본과 (있으면)시행중 기준선본을 **함께** 저장 · **revision·상태가 같으면 저장 생략**(증분, D16) · `lia.ingest` 계측(span/timer).
- **담당 안 함:** 외부 API 호출·목록 순회·재시도·실패 격리([[IngestJob]]) · 조문 병합·부칙 필터([[Normalizer]]) · 신설/삭제/이동 판정([[DiffBuilder]]) · 영속([[LawStore]]) · 임베딩·색인([[RAGIndexer]], [[IngestJob]] `INDEX`). **오케스트레이터는 로직을 위임만 한다.**

## Collaborators
- **[[Normalizer]]** · **[[DiffBuilder]]** · **[[LawStore]]** — 순수 위임.
- **ObservationRegistry** — 계측(null이면 `NOOP`).
- 외부 시스템: Postgres(Store 경유). **외부 API는 부르지 않는다** — fetch는 [[IngestJob]]의 `SyncHandler`가 하고 `RawLaw`를 넘긴다.
- 호출자: [[IngestJob]] `SyncHandler` · `SeedRunner`(dev 시드) · 통합 테스트.

## Business Flow
**`store(pendingRaw, baselineRaw) → IngestResult`** — 조립 단위(API 불필요).
1. `pending = normalize(pendingRaw)`, `baseline = baselineRaw==null ? null : normalize(baselineRaw)`.
2. `diffed = diffBuilder.build(pending, baseline)` — 기준선 null(제정)이면 변경 조문 전부 `신설`.
3. 저장된 행의 `revision`·`status`와 비교 → **하나라도 다르거나 행이 없으면** `lawStore.upsert(diffed)`, 둘 다 같으면 생략. 시행예정본의 저장 revision은 **`<본문 revision>+<기준선 revision 또는 none>`** — diff가 기준선에도 의존하므로 기준선만 바뀌어도(시행 전환 등) 다시 저장된다. 상태까지 비교하는 이유: `철회`된 버전이 같은 내용으로 다시 나타나면 `시행예정`으로 복원돼야 한다.
4. `baseline != null`이면 같은 방식으로 기준선(시행중 정본)도 비교 후 upsert → 이후 `findBaseline`으로 조회 가능.
5. `IngestResult(lawId, effectiveDate, changedCount, hasBaseline, baselineEffectiveDate, pendingChanged)` 반환 — `pendingChanged`가 true면 호출자([[IngestJob]])가 `INDEX`를 등록하고, `baselineEffectiveDate`로 더 오래된 시행중본을 `과거`로 전환한다.

배치 진입점이던 `ingestPending()`은 [[IngestJob]]이 대체해 **제거**됐다(D62).

## Transaction Boundary
- **정본 저장은 upsert 멱등**([[LawStore]] `ON CONFLICT`)이라 스스로 트랜잭션을 열지 않는다 — 재실행 안전. 호출자 트랜잭션이 있으면 참여한다: [[IngestJob]] `SyncHandler`가 `store`와 `INDEX` 등록을 **한 트랜잭션**으로 묶어, 저장만 되고 색인 등록이 빠지는 상태(재시도 시 revision이 같아 영영 색인 안 됨)를 막는다.
- 경계 단위 = **법령 1건**(pending + 그 기준선). 실패 격리·재시도는 [[IngestJob]] 작업 큐가 한다.
- `store()` 안의 두 upsert(시행예정·시행중)는 원자적 묶음이 **아니다** — 멱등이라 재실행으로 수렴하므로 분산 트랜잭션 불필요(D54 SSOT 모델).

## Business Rules
- **제정 법령 = 기준선 없음** — `fetchCurrent`가 null(현행본 부재, [[law-domain-basics]] §3) → `DiffBuilder(baseline=null)` → 전부 신설, 시행중 정본 미저장 → `findBaseline` empty. 오류가 아니라 정상 경로.
- **기준선본도 저장한다** — diff의 기준이 된 시행중본을 함께 upsert해, 이후 `findBaseline`·정확 조회·context 조립에서 재사용.
- **revision이 같으면 쓰지 않는다** — 불필요한 쓰기·색인·캐시 무효화를 막는다(두 번 돌아도 결과 동일, [[concurrency-and-reliability]] §2).
- 정본 단위 = **`(lawId, effectiveDate)`**(D43).

## Invariants
- 저장되는 시행예정 정본은 **항상 diff를 거친 것**(`diffBuilder.build` 산출) — raw 정규화본을 바로 저장하지 않는다.
- `IngestResult.hasBaseline == (baselineRaw != null)` — 제정/개정 판별의 단일 근거.
- `pendingChanged == false`이면 이번 호출에서 시행예정 행에 쓰기가 일어나지 않았다.

## Error Handling
- 단계 위임 중 예외는 **감싸지 않고 전파**(fail-fast) — 오류 분류·재시도·격리는 [[IngestJob]]. 오케스트레이터는 예외를 삼키지 않는다.
- `Observation`으로 감싸 실패도 계측에 error로 기록된다.

## Side Effects
- **DB 쓰기**([[LawStore]] upsert — revision이 바뀐 경우만) · 로그·메트릭. 외부 API 호출 없음.

## Design Constraints
- **오프라인 전용**(D40) — 요청 경로(온라인)에서 호출하지 않는다.
- `@Component`지만 요청 스코프 빈이 아니며, DB 없는 스프링 컨텍스트에서도 빈 생성은 성립해야 한다(카나리아 `CredentialLoadingTest`로 회귀 감시).
- 부분 실패·재시도·병렬도 정책은 [[IngestJob]](D62)에서 확정.

## 실측 검증
- 실 Postgres 조립 통합테스트 2건(Testcontainers, `IngestServiceIntegrationTest`):
  - **기준선 있음** — 주택법 pending↔baseline → 제18조 `개정`·`diffVsCurrent` 저장, `findBaseline` 존재.
  - **제정(baseline=null)** — 변경 조문 전부 `신설`, `findBaseline` empty.
- 재호출 시 `pendingChanged=false`·쓰기 없음 · 기준선만 바뀌어도 `pendingChanged=true` · 철회된 행에 같은 입력 → `시행예정` 복원(신규 테스트 3건).
