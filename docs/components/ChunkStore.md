---
title: ChunkStore — 클래스 스펙 (spec-first)
status: Draft
date: 2026-08-27
tags: [component, store, vector, pgvector, rag]
related: ["components/component-specs.md", "components/RAGIndexer.md", "components/Embedder.md", "components/LawStore.md", "adr/decision-log.md"]
---

# ChunkStore

> 벡터 chunks **적재·검색** — Spring AI `PgVectorStore`(pgvector, HNSW·코사인) 래핑. [[LawStore]]에서 분리한다: LawStore는 `JdbcClient`로 **JSONB 정본**을 다루고, chunks는 `PgVectorStore`(다른 라이브러리·다른 테이블)라 한 클래스에 두 영속 메커니즘을 섞지 않는다. 스키마 SSOT: [[component-specs]] §1.

## Responsibility
- **담당:** `Chunk`(source_id·content·metadata) → 벡터 **upsert**(source_id 기준 삭제-후-삽입, 멱등) · 유사도 **search**(top-k, namespace/lawId 필터). `Chunk ↔ Spring AI Document` 매핑.
- **담당 안 함:** 임베딩 *생성*([[Embedder]]) · 청킹·대상 선별([[RAGIndexer]]) · 정본 저장([[LawStore]] `law_versions`) · 검색 *전략*([[SourceAnalyzer]]).

## Collaborators
- **Spring AI `PgVectorStore`** — 벡터 테이블(`vector_store`: id·content·metadata jsonb·embedding) 관리·HNSW 색인·유사도 검색.
- 소비자: [[RAGIndexer]](적재 upsert) · (후속) [[SourceAnalyzer]]/dispatch(검색).

## Persistence Contract
- `upsert(List<Chunk>)` — `source_id` 기준 **삭제-후-삽입**. 같은 source_id 재적재는 덮어쓰기(revision 변동 재색인 안전). 배치.
- `search(String query, int k, filter?) → List<Chunk>` — 유사도 top-k. filter=`namespace=pending`(+lawId 등). 임베딩은 `PgVectorStore`가 내부에서 질의 텍스트를 [[Embedder]] 공유 모델로 임베딩. **이번 증분에서는 라운드트립 검증용**이 주 용도(본격 검색은 [[SourceAnalyzer]] landing 시).
- (후속) `deleteByLaw(lawId, efYd)` — 법령 폐기·재색인 정리.

## Schema · Index
- 테이블 = **Spring AI `PgVectorStore`가 소유**(`initialize-schema=true`) — 관계형(=Flyway) 소유와 분리(LawStore.md 원칙, D34).
- `vector_store(id uuid, content text, metadata jsonb, embedding vector(1536))` + **HNSW**(코사인). `dim`은 [[Embedder]] `EmbeddingProperties.dim`(1536)과 반드시 일치 — 설정: `spring.ai.vectorstore.pgvector.dimensions=${lia.embedding.dim}`.
- metadata 필터 키: `source_id·lawId·efYd·kind·namespace·changed·articleNo`.

## Invariants
- **`law_versions`가 SSOT, chunks는 파생** — 언제든 재생성 가능(인용의 진실은 정본). `chunk.metadata.source_id`는 [[LawStore]]의 실제 조문과 일치해야 한다(그라운딩 무결성).
- upsert 멱등 — source_id 삭제-후-삽입이라 재실행 수렴.
- 색인·검색 **동일 임베딩 모델·dim**([[Embedder]] 공유) — 벡터공간 일치.

## Error Handling
- PgVectorStore/DB 장애 → 상위(적재 배치·질의 경로)로 전파. 부분 실패는 source_id 멱등으로 재실행 안전.

## Side Effects
- **DB 쓰기·읽기**(pgvector). 검색 시 질의 임베딩을 위해 [[Embedder]] 경유 **외부 API 호출** 가능.

## Observability
- Spring AI **`db.vector.client.operation`** 내장 계측(add/query)에 위임 — 별도 `lia.*` 없음. 질의 임베딩은 `gen_ai.*`([[Embedder]]).

## Design Constraints
- **ADR-001 불변** — dim 1536(D32), 벡터 부피 주축은 diff 기준선 본문. pgvector `CREATE EXTENSION vector`(D34, 도커 이미지 `pgvector/pgvector:pg16`에 포함).
- **스키마 소유권** — 벡터=PgVectorStore, 관계형=Flyway. 이중 관리 금지.

## 검증
- **통합**(Testcontainers `pgvector/pgvector:pg16`): `upsert`→`search` 라운드트립(넣은 source_id가 top-k에), 멱등 재upsert(중복 없음). `FakeEmbedder`로 결정론 벡터 주입 또는 실 임베딩 게이트.
