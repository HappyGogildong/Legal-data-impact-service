---
title: LawStore — 클래스 스펙 (spec-first)
status: Draft
date: 2026-08-22
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

## Persistence Contract
- **정본 (law_versions)**
  - `upsert(Law)` — `(lawId, effectiveDate)` 단위 삽입/갱신. `revision` 다르면 payload·인덱스 갱신.
  - `find(lawId, effectiveDate) → Law?` — 특정 버전 정본.
  - `findBaseline(lawId) → Law?` — 같은 lawId의 **시행중본**. 없으면 null(제정, [[law-domain-basics]] §3).
  - `findPending(criteria) → Law[]` — 해소·목록용(status=시행예정, 시도·도메인 필터).
- **벡터 (chunks)** — [[ChunkStore]] 소유. 여기서 다루지 않는다.
- **캐시 (후속)**
  - `LawFacts` — 키 `lawId@efYd + revision`(프로필 무관).
  - `ImpactResult` 답변 캐시 — 키 `hash(정규화 질문)+law_ref+profileHash+prompt_version+revision`(완전 동일 질의, D51).

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
- 격리 **Read Committed**(적재-조회 병행). 벡터 재색인은 삭제-후-삽입(source_id 기준) 멱등.

## Invariants
- 정본 단위 = **`(lawId, effectiveDate)`**(lawId 단독 아님 — 복수 시행예정본, D43).
- **`law_versions`가 SSOT, chunks는 파생**(언제든 재생성 가능; 인용의 진실은 정본). chunk↔정본 정합은 [[ChunkStore]]가 `source_id`로 보장.

## Error Handling
- 제약 위반 → upsert 충돌 해소(예외 아님). DB 장애 → 상위로 전파(적재 배치가 재시도).

## Side Effects
- **DB 쓰기**(law_versions upsert). 읽기 조회. 외부 API 없음(임베딩 생성은 Embedder, 벡터 저장은 ChunkStore).

## Design Constraints
- **ADR-001 불변** — 시행예정 899건, 벡터 부피 주축은 diff 기준선 본문(~0.4GB). dim **1536**(D32).
- pgvector `CREATE EXTENSION vector`(D34). 스키마 소유권: 관계형=Flyway, 벡터=PgVectorStore.

## 구현 순서 (near-term → later)
1. **완료:** `law_versions` upsert + `find`/`findBaseline` + Flyway 스키마 → 적재 파이프라인(Normalizer→DiffBuilder→Store).
2. 벡터 chunks는 [[ChunkStore]]로 분리 — [[Embedder]]·[[RAGIndexer]]와 함께.
3. `LawFacts`·`ImpactResult` 캐시 — Layer A/B·[[AnalysisEngine]] landing 시.
