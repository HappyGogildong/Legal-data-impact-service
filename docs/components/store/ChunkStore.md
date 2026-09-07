---
title: ChunkStore — 클래스 스펙 (spec-first)
status: Draft
date: 2026-08-27
tags: [component, store, vector, pgvector, rag]
related: ["components/component-specs.md", "components/index/RAGIndexer.md", "components/embed/Embedder.md", "components/store/LawStore.md", "adr/decision-log.md"]
---

# ChunkStore

> 벡터 chunks **적재·검색** — Spring AI `PgVectorStore`(pgvector, HNSW·코사인) 래핑. [[LawStore]]에서 분리한다: LawStore는 `JdbcClient`로 **JSONB 정본**을 다루고, chunks는 `PgVectorStore`(다른 라이브러리·다른 테이블)라 한 클래스에 두 영속 메커니즘을 섞지 않는다. 스키마 SSOT: [[component-specs]] §1.

## Responsibility
- **담당:** `Chunk`(source_id·content·metadata) → **정본 단위 replace**(`(lawId, efYd)` 청크를 현재 세트로 완전 교체) · 유사도 **search**(top-k, namespace/lawId 필터). `Chunk ↔ Spring AI Document` 매핑.
- **담당 안 함:** 임베딩 *생성*([[Embedder]]) · 청킹·대상 선별([[RAGIndexer]]) · 정본 저장([[LawStore]] `law_versions`) · 검색 *전략*([[SourceAnalyzer]]).

## Collaborators
- **Spring AI `PgVectorStore`** — 벡터 테이블(`vector_store`: id·content·metadata jsonb·embedding) 관리·HNSW 색인·유사도 검색. **`add`/`similaritySearch` 시 설정된 `EmbeddingModel`(공유 OpenAI 모델)로 내부 임베딩** — 적재·검색이 같은 모델을 쓰는 게 여기서 보장된다.
- 소비자: [[RAGIndexer]](적재 upsert) · (후속) [[SourceAnalyzer]]/dispatch(검색).
- [[Embedder]] 포트는 이 경로에 없음 — PgVectorStore가 EmbeddingModel을 직접 쓴다(Embedder는 설정 핀·eval 유틸).

- `Chunk`(source_id·content·metadata) → Spring AI `Document`로 매핑해 `PgVectorStore.add`에 넘긴다. **content 임베딩은 PgVectorStore가 내부에서** 수행(우리가 벡터를 만들지 않음).

## Persistence Contract
- `replaceVersion(lawId, efYd, chunks)` — 한 정본 `(lawId, efYd)`의 청크를 **현재 세트로 완전 교체**(그 정본 기존 청크 전부 삭제 → 삽입). **재색인 시 저장 상태 == 현재 법령 상태**를 보장 — 분할 개수 감소·조문 삭제/이동으로 사라진 `source_id`가 **stale로 남지 않는다**. `chunks`가 비면 그 정본 청크를 전부 삭제(삭제 반영). add 시 `content` 자동 임베딩.
  - **왜 source_id 단위 upsert가 아닌가:** 한 정본의 청크 집합은 함께 바뀌므로(과대 조문 분할 수 변동 등) 개별 id 덮어쓰기로는 사라진 청크를 정리하지 못한다 — 정본 단위 replace라야 "재실행 = 현재 상태" 불변식이 선다.
- `search(String query, int k, filter?) → List<Chunk>` — 유사도 top-k. filter=`namespace=pending`(+lawId 등). `PgVectorStore`가 **질의를 내부 임베딩**해 검색. **이번 증분에서는 라운드트립 검증용**이 주 용도(본격 검색은 [[SourceAnalyzer]] landing 시).

## Schema · Index
- 테이블 = **Spring AI `PgVectorStore`가 소유**(`initialize-schema=true`) — 관계형(=Flyway) 소유와 분리(LawStore.md 원칙, D34).
- `vector_store(id uuid, content text, metadata jsonb, embedding vector(1536))` + **HNSW**(코사인). `dim`은 [[Embedder]] `EmbeddingProperties.dim`(1536)과 반드시 일치 — 설정: `spring.ai.vectorstore.pgvector.dimensions=${lia.embedding.dim}`.
- metadata 필터 키: `source_id·lawId·efYd·kind·namespace·changed·articleNo`.

## Invariants
- **`law_versions`가 SSOT, chunks는 파생** — 언제든 재생성 가능(인용의 진실은 정본). `chunk.metadata.source_id`는 [[LawStore]]의 실제 조문과 일치해야 한다(그라운딩 무결성).
- **재색인 = 현재 상태** — `replaceVersion`은 정본 단위 완전 교체라, 같은 정본을 다시 색인하면 저장 청크 집합이 현재 법령과 정확히 일치한다(stale 없음). 재실행 안전·멱등.
- 색인·검색 **동일 임베딩 모델·dim** — PgVectorStore가 add/search에 같은 `EmbeddingModel`을 써 벡터공간 일치.

## Error Handling
- PgVectorStore/DB 장애 → 상위(적재 배치·질의 경로)로 전파. 부분 실패는 정본 단위 replace라 재실행 시 현재 상태로 수렴(안전).

## Side Effects
- **DB 쓰기·읽기**(pgvector). add/search 시 PgVectorStore가 `EmbeddingModel`로 **외부 임베딩 API 호출**(content·질의).

## Observability
- Spring AI **`db.vector.client.operation`** 내장 계측(add/query)에 위임 — 별도 `lia.*` 없음. 질의 임베딩은 `gen_ai.*`([[Embedder]]).

## Design Constraints
- **ADR-001 불변** — dim 1536(D32), 벡터 부피 주축은 diff 기준선 본문. pgvector `CREATE EXTENSION vector`(D34, 도커 이미지 `pgvector/pgvector:pg16`에 포함).
- **스키마 소유권** — 벡터=PgVectorStore, 관계형=Flyway. 이중 관리 금지.

## 검증
- **통합**(Testcontainers `pgvector/pgvector:pg16`): `replaceVersion`→`search` 라운드트립 · **재색인 stale 제거**(분할 3→2일 때 사라진 하위 청크 제거) · **스코프 격리**(다른 정본 미삭제). PgVectorStore에 **결정론 Fake `EmbeddingModel`** 주입 → 실 API·비용 없이 add/search 임베딩 재현.
