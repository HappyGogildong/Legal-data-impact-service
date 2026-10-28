---
title: LawStore — 클래스 스펙 (spec-first)
status: Draft
date: 2026-10-29
tags: [component, store, repository, pgvector]
related: ["components/component-specs.md", "components/normalize/Normalizer.md", "components/embed/Embedder.md", "components/dispatch/QueryDispatcher.md", "reference/law-domain-basics.md", "adr/decision-log.md"]
---

# LawStore

> 법령 **정본(원문) 영속·조회** + **벡터 chunks**(RAG 검색) + (후속) Layer A/B 캐시. **구현 전 계약(spec-first)** — 이 스펙이 구현을 이끈다. 스키마 SSOT: [[component-specs]] §1. 저장소: ADR-001(Postgres+pgvector).

## Responsibility
- **담당:** `Law` 정본 upsert·조회(`(lawId, effectiveDate)` 단위) · **baseline 조회**(lawId→시행중본) · (후속) `LawFacts`·`ImpactResult` 캐시.
- **담당 안 함:** 임베딩 *생성*([[Embedder]]) · **벡터 chunks 적재·검색([[ChunkStore]])** · 정규화([[Normalizer]]) · diff([[DiffBuilder]]) · 검색 전략(SourceAnalyzer/RAG). Store는 **정본 저장·조회만**.

> **chunks는 [[ChunkStore]]가 소유한다.** LawStore는 `JdbcClient`로 JSONB 정본을, ChunkStore는 `PgVectorStore`로 벡터를 다룬다 — 다른 라이브러리·다른 테이블이라 한 클래스에 두 영속 메커니즘을 섞지 않는다(2026-08-27 구조 결정).

## Collaborators
- **Postgres 16 + pgvector**(로컬 도커 `pgvector/pgvector:pg16`, prod RDS — D34).
- 영속: **`JdbcClient`**(JSONB payload 매핑 — Hibernate 불필요) + Flyway(관계형 마이그레이션).
- 벡터는 [[ChunkStore]](Spring AI `PgVectorStore`)가 별도로 소유.
- 소비자: [[SourceAnalyzer|LawLookup]] 구현(해소) · [[AnalysisEngine]](context 조립·정본 인용) · [[QueryDispatcher]](`LawSource`로 정본·기준선 조회).

## LawSource 포트 (정본 정확조회)

`LawStore`가 구현하는 **좁은 읽기 포트**(`store.LawSource`) — `find(lawId, efYd)`(시행예정 정본)·`findBaseline(lawId)`(시행중 기준선) 두 계약만 노출한다.

- **왜 포트인가:** 소비자([[QueryDispatcher]])를 `LawStore` 전체가 아니라 **필요한 조회 계약**에만 결합시키고, 단위 테스트 시임(Fake)을 준다. 의존 방향은 dispatch → store(정방향).
- **`resolve.LawLookup`과 다른 관심사** — 그쪽은 *커넥터 기반 해소*(이름→ref), 이쪽은 **저장된 정본 읽기**. 벡터 검색이 아니다(Discovery 전용, D56).
- **계약:** `find`는 미적재면 empty, `findBaseline`은 제정이라 없으면 empty(정상, D42).

## 저장 모델 (JSONB 정본 + pgvector chunks)

**두 저장소, 두 목적, 두 소유자** ([[law-domain-basics]] 참고):

| 테이블 | 1행 | 목적 | 소유 |
|---|---|---|---|
| `law_versions` | 법령 한 버전 | **정본 원문**(정확 조회·context·baseline) | **LawStore** (`JdbcClient`+Flyway) |
| `vector_store` | 조문·요약 청크 | **임베딩 검색**(RAG recall) | [[ChunkStore]] (`PgVectorStore`) |

검색은 chunks(pgvector)로 후보를 찾고, `source_id`로 `law_versions`에서 **정본을 되짚어** 인용한다(그라운딩) — 두 저장소를 잇는 키가 `source_id`.

## store 패키지 테이블

`com.lia.core.store` 의 저장소 전체. 정본(`law_versions`) 외에는 각자 좁은 용도의 별도 테이블·클래스다(한 클래스에 몰지 않는다).

| 테이블 (Flyway) | 클래스 · 포트 | 용도 | 상세 |
|---|---|---|---|
| `law_versions` (V1·V5·V6·V7) | `LawStore` · `LawSource` | 정본·기준선 · 배치 상태(V5) · 목록 인덱스(V6) | 이 문서 |
| (같은 테이블 읽기) | `LawCatalogStore` | 곧 시행 목록(키셋 커서)·한 법령의 버전들(D65) | [[LawCatalogApi]] |
| (여러 테이블 집계) | `DataStatusStore` | 데이터 현황(적재 시각·건수) | [[DataStatusApi]] |
| `vector_store` | `PgVectorChunkStore` · `ChunkStore` | 조문·요약 청크 임베딩 | [[ChunkStore]] |
| `law_summaries` (V9) | `LawSummaryStore` · `LawSummaries`(구현 `pipeline.analyze.GroundedSummaries`) | 쉬운 요약 선계산(D67) — 버전마다 1행. 저장 revision·프롬프트 버전이 지금과 같을 때만 읽고, 지금의 근거 집합으로 인용 검증을 다시 통과해야 쓴다 | [[IngestJob]](SUMMARIZE) · [[QueryDispatcher]](SummaryHandler) |
| `law_views_daily` (V8) | `LawViewStore` · `LawViews` | 자주 찾는 법령(D66) — 서울 날짜별 조회 횟수만(누가 봤는지 없음) | [[TrendingApi]] |
| `personalization_usage_daily` (V10) | `PersonalizationUsageStore` · `PersonalizationQuota` | 사용자별 하루 개인화 한도(D69) — 원자 upsert 차감, 계정 삭제 시 cascade | [[QueryDispatcher]] |
| `accounts`·`user_profiles` (V2~V4) | `auth.AccountStore`·`profile.UserProfileStore` | 계정·프로필·동의 | [[Auth]] · [[UserProfile]] |
| `ingest_*` (V5) | `pipeline.batch.TaskQueue`·`IngestRunStore` | 배치 작업 큐·실행 기록 | [[IngestJob]] |

## Persistence Contract
- **정본 (law_versions)**
  - `upsert(Law)` — `(lawId, effectiveDate)` 단위 삽입/갱신. `revision` 다르면 payload·인덱스 갱신.
  - `find(lawId, effectiveDate) → Law?` — 특정 버전 정본.
  - `findBaseline(lawId) → Law?` — 같은 lawId의 **시행중본**. 없으면 null(제정, [[law-domain-basics]] §3).
  - `findPending(criteria) → Law[]` — 해소·목록용(status=시행예정, 시도·도메인 필터).
- **상태 값**(D62): `시행중` · `시행예정` · **`과거`**(같은 법령의 더 새 시행중본에 대체됨) · **`철회`**(목록에서 사라진 시행예정본). 지나간 버전도 **행을 지우지 않는다** — 과거 답변이 인용한 `source_id`를 계속 되짚을 수 있게. `findBaseline`은 `시행중`만 본다.
- **배치용 계약**([[IngestJob]]): 탐색용 시행예정 버전 목록(`lawId`·`efYd`·`mst`·`missing_since`) · 상태 변경(**컬럼과 payload의 `status`를 함께** — `find`는 payload를 역직렬화) · 더 오래된 `시행중` → `과거` 일괄 전환 · `missing_since` 설정/해제 · 같은 법령의 남은 시행예정본 조회 · 증분 비교용 저장 revision 조회(**상태가 같을 때만** 일치로 본다 — 철회본이 같은 내용으로 다시 나타나도 `시행예정`으로 복원되게). 컬럼 `missing_since date`(완주 목록에서 처음 안 보인 날, KST)는 `V5__ingest_batch.sql`.
- 배치 쓰기는 호출자 트랜잭션에 참여한다(`JdbcClient` → 같은 `DataSource`) — `SyncHandler`가 저장과 `INDEX` 등록을 한 트랜잭션으로 묶는다.
- **벡터 (chunks)** — [[ChunkStore]] 소유. 여기서 다루지 않는다.
- **쉬운 요약 저장본** — `law_summaries`(위 표, D67). 답변 캐시가 아니라 배치가 미리 만든 Layer A 결과.
- **캐시 (후속)**
  - `LawFacts` — 키 `lawId@efYd + revision`(프로필 무관).
  - `ImpactResult` 답변 캐시 — 키 `hash(정규화 질문)+law_ref+profileHash+prompt_version+revision`(완전 동일 질의, D51). Layer B 개인화 답은 지금 저장하지 않는다(D69).

## Query · Index
- `law_versions`: **UNIQUE `(lawId, effectiveDate)`**. 보조 인덱스 `status`, `lawId`(baseline 조회). `payload jsonb`(필요 시 GIN).
- 벡터 `vector_store`(HNSW·코사인·metadata 필터)는 [[ChunkStore]] 참조.
- 스키마 스케치(관계형만 — 벡터는 PgVectorStore 소유):
  ```sql
  CREATE TABLE law_versions (
    law_id text NOT NULL, effective_date date NOT NULL,
    revision text NOT NULL, status text NOT NULL, title text NOT NULL,
    baseline_law_id text, payload jsonb NOT NULL, last_seen timestamptz NOT NULL,
    PRIMARY KEY (law_id, effective_date));
  ```

## Transaction / Lock
- **upsert 멱등** — `INSERT … ON CONFLICT (law_id, effective_date) DO UPDATE`(revision·payload·last_seen). 재적재 안전.
- 격리 **Read Committed**(적재-조회 병행). 벡터 재색인은 정본 `(lawId, efYd)` 단위 삭제+삽입을 한 트랜잭션으로([[ChunkStore]]) — 멱등.

## Invariants
- 정본 단위 = **`(lawId, effectiveDate)`**(lawId 단독 아님 — 복수 시행예정본, D43).
- **`law_versions`가 SSOT, chunks는 파생**(언제든 재생성 가능; 인용의 진실은 정본). chunk↔정본 정합은 [[ChunkStore]]가 `source_id`로 보장.

## Error Handling
- 제약 위반 → upsert 충돌 해소(예외 아님). DB 장애 → 상위로 전파([[IngestJob]]이 작업 단위로 재시도).

## Side Effects
- **DB 쓰기**(law_versions upsert). 읽기 조회. 외부 API 없음(임베딩 생성은 Embedder, 벡터 저장은 ChunkStore).

## Design Constraints
- **ADR-001 불변** — 시행예정 899건, 벡터 부피 주축은 diff 기준선 본문(~0.4GB). dim **1536**(D32).
- pgvector `CREATE EXTENSION vector`(D34). 스키마 소유권: 관계형=Flyway, 벡터=PgVectorStore.

## 구현 순서 (near-term → later)
1. **완료:** `law_versions` upsert + `find`/`findBaseline` + Flyway 스키마 → 적재 파이프라인(Normalizer→DiffBuilder→Store).
2. 벡터 chunks는 [[ChunkStore]]로 분리 — [[Embedder]]·[[RAGIndexer]]와 함께.
3. **완료:** 쉬운 요약 저장본 `law_summaries`(D67) · 조회 집계(D66) · 개인화 한도(D69) — 위 §store 패키지 테이블.
4. `LawFacts`·답변 캐시 — 병목을 지표로 증명한 뒤(D48·D51).
