---
title: LawStore — 클래스 스펙 (spec-first)
status: Draft
date: 2026-08-22
tags: [component, store, repository, pgvector]
related: ["components/component-specs.md", "components/Normalizer.md", "components/Embedder.md", "reference/law-domain-basics.md", "adr/decision-log.md"]
---

# LawStore

> 법령 **정본(원문) 영속·조회** + **벡터 chunks**(RAG 검색) + (후속) Layer A/B 캐시. **구현 전 계약(spec-first)** — 이 스펙이 구현을 이끈다. 스키마 SSOT: [[component-specs]] §1. 저장소: ADR-001(Postgres+pgvector).

## Responsibility
- **담당:** `Law` 정본 upsert·조회(`(lawId, effectiveDate)` 단위) · **baseline 조회**(lawId→시행중본) · 벡터 chunks 위임(add/search) · (후속) `LawFacts`·`ImpactResult` 캐시.
- **담당 안 함:** 임베딩 *생성*([[Embedder]]) · 정규화([[Normalizer]]) · diff([[DiffBuilder]]) · 검색 전략(SourceAnalyzer/RAG). Store는 **저장·조회만**.

## Collaborators
- **Postgres 16 + pgvector**(로컬 도커 `pgvector/pgvector:pg16`, prod RDS — D34).
- **Spring AI `PgVectorStore`** — chunks(임베딩) 테이블 관리.
- 영속: **Spring Data JDBC**(JSONB payload 매핑 — Hibernate 불필요) + Flyway(마이그레이션).
- 소비자: [[SourceAnalyzer|LawLookup]] 구현(해소) · [[RAGIndexer]](chunks 적재) · [[AnalysisEngine]](context 조립).

## 저장 모델 (JSONB 정본 + pgvector chunks)

**두 저장소, 두 목적** ([[law-domain-basics]] 참고):

| 테이블 | 1행 | 목적 |
|---|---|---|
| `law_versions` | 법령 한 버전 | **정본 원문**(정확 조회·context·baseline) |
| `chunks`(PgVectorStore) | 조문 청크 | **임베딩 검색**(RAG recall) |

검색은 `chunks`(pgvector)로 후보를 찾고, `source_id`로 `law_versions`에서 **정본을 되짚어** 인용한다(그라운딩).

## Persistence Contract
- **정본 (law_versions)**
  - `upsert(Law)` — `(lawId, effectiveDate)` 단위 삽입/갱신. `revision` 다르면 payload·인덱스 갱신.
  - `find(lawId, effectiveDate) → Law?` — 특정 버전 정본.
  - `findBaseline(lawId) → Law?` — 같은 lawId의 **시행중본**. 없으면 null(제정, [[law-domain-basics]] §3).
  - `findPending(criteria) → Law[]` — 해소·목록용(status=시행예정, 시도·도메인 필터).
- **벡터 (chunks, PgVectorStore 위임)**
  - `index(chunks)` — 조문 청크(content·metadata{source_id·lawId·efYd·namespace}·embedding) 적재.
  - `search(queryEmbedding, k, filter) → Chunk[]` — 유사도 top-k(namespace=`pending` 등 필터).
- **캐시 (후속)**
  - `LawFacts` — 키 `lawId@efYd + revision`(프로필 무관).
  - `ImpactResult` 답변 캐시 — 키 `hash(정규화 질문)+law_ref+profileHash+prompt_version+revision`(완전 동일 질의, D51).

## Query · Index
- `law_versions`: **UNIQUE `(lawId, effectiveDate)`**. 보조 인덱스 `status`, `lawId`(baseline 조회). `payload jsonb`(필요 시 GIN).
- `chunks`: pgvector **HNSW**(코사인), `metadata` 필터(namespace·lawId).
- 스키마 스케치:
  ```sql
  CREATE TABLE law_versions (
    law_id text NOT NULL, effective_date date NOT NULL,
    revision text NOT NULL, status text NOT NULL, title text NOT NULL,
    baseline_law_id text, payload jsonb NOT NULL, last_seen timestamptz NOT NULL,
    PRIMARY KEY (law_id, effective_date));
  -- chunks 는 Spring AI PgVectorStore 가 관리(vector_store: id, content, metadata jsonb, embedding vector(1536))
  ```

## Transaction / Lock
- **upsert 멱등** — `INSERT … ON CONFLICT (law_id, effective_date) DO UPDATE`(revision·payload·last_seen). 재적재 안전.
- 격리 **Read Committed**(적재-조회 병행). 벡터 재색인은 삭제-후-삽입(source_id 기준) 멱등.

## Invariants
- 정본 단위 = **`(lawId, effectiveDate)`**(lawId 단독 아님 — 복수 시행예정본, D43).
- **`law_versions`가 SSOT, `chunks`는 파생**(언제든 재생성 가능; 인용의 진실은 정본).
- `chunk.metadata.source_id`는 반드시 `law_versions`의 실제 조문과 일치(그라운딩 무결성).

## Error Handling
- 제약 위반 → upsert 충돌 해소(예외 아님). DB 장애 → 상위로 전파(적재 배치가 재시도).

## Side Effects
- **DB 쓰기**(law_versions upsert, chunks 색인). 읽기 조회. 외부 API 없음(임베딩 생성은 Embedder).

## Design Constraints
- **ADR-001 불변** — 시행예정 899건, 벡터 부피 주축은 diff 기준선 본문(~0.4GB). dim **1536**(D32).
- pgvector `CREATE EXTENSION vector`(D34). 스키마 소유권: 관계형=Flyway, 벡터=PgVectorStore.

## 구현 순서 (near-term → later)
1. **near-term:** `law_versions` upsert + `find`/`findBaseline` + Flyway 스키마 → 적재 파이프라인(Normalizer→DiffBuilder→Store) 완성.
2. `chunks`(PgVectorStore) `index`/`search` — [[Embedder]]·[[RAGIndexer]]와 함께.
3. `LawFacts`·`ImpactResult` 캐시 — Layer A/B·[[AnalysisEngine]] landing 시.
