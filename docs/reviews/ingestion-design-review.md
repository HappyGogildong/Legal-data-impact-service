---
title: 적재 설계 검토 — 내부 정합성 + 외부 대조
status: Review
date: 2026-08-22
tags: [review, ingestion, rag, architecture]
related: ["components/LawStore.md", "components/Embedder.md", "components/RAGIndexer.md", "adr/decision-log.md", "reference/law-domain-basics.md"]
---

# 적재 설계 검토 — 내부 정합성 + 외부 대조

적재 3종(Law Store·Embedder·RAG Indexer) spec-first 완료 시점의 설계 검토. 목적: ① 스펙 간 계약이 맞물리는지 ② 우리 기술 결정이 업계·연구 접근과 어떻게 대조되는지.

## 0. 적재 흐름 (오프라인 배치)

```
LawConnector.fetchPending/fetchCurrent
  └(내부) LawEnvelope 봉투 파싱 → RawLaw            ← LawEnvelope=커넥터 내부 파싱(별도 단계 아님)
     → Normalizer:  RawLaw → Law                    ← 정규화(조문 병합·부칙 필터·위임·revision)
        → DiffBuilder: pending + baseline → Law(+diff, 신설/삭제)   [기준선=fetchCurrent, 제정이면 null→전부 신설]
           ├─→ LawStore.upsert → law_versions (JSONB 정본)      ← 저장 ① 원문(SSOT)
           └─→ RAGIndexer → Embedder(passage) → LawStore.chunks  ← 저장 ② 벡터(RAG)
```

## A. 내부 정합성 — 3종 스펙이 맞물리나

**✓ 맞물리는 것**
- `source_id`(`LAW:{lawId}@{efYd}:art:{no}`)가 RAGIndexer = [[component-specs]] §1 = `Law.sourceId`로 일치.
- **baseline null 체인**: `fetchCurrent`→null(제정) → `LawStore.findBaseline`→null → `DiffBuilder(baseline=null)`→전부 신설.
- **네임스페이스**: RAGIndexer가 `pending`에 쓰고 [[SourceAnalyzer]]가 `pending`에서 읽음.
- **경계**: 청킹=RAGIndexer, 임베딩=Embedder, 저장=LawStore — "담당 안 함"이 겹치지 않음.

**⚠️ 스펙에 못박을 2가지**
1. **차원 커플링** — `LawStore.chunks`를 `vector(1536)`로 고정했으나 이는 **Embedder 벤더(D33 미확정)에 종속**. Upstage(dim=4096) 선택 시 chunks도 4096이어야 함. 불변식 **`chunks dim = Embedder.dim()`**를 명시하고, 벤치로 벤더 확정 후 스키마를 박는다.
2. **RAGIndexer 시퀀싱** — **조문 청크 경로는 Normalizer+DiffBuilder만으로 지금 구현 가능**하지만 **요약/LawFacts 경로는 Layer A(LawFacts, 미구현)에 종속**. 구현이 둘로 갈린다(article=near-term, summary=LawFacts 이후).

## B. 외부 대조 — 우리 결정 vs 업계·연구 (2025–26)

| 결정 | 우리 | 업계·연구 | 평가 |
|---|---|---|---|
| **벡터 저장** (D34/D54) | pgvector | ~10M 벡터 이하면 **pgvector가 정답 기본값**(joins·트랜잭션·RBAC 공짜). 우리 코퍼스 <1M | ✅ 강하게 정합. 대안: Qdrant(속도)·Weaviate(하이브리드 내장) |
| **조문 단위 청킹** (D55) | 변경 조문=1청크 + 과대 오버랩 분할 | **법률 문서는 clause/section 단위 권장**(각 조항=검색 단위). 긴 조각 5–20% 오버랩 | ✅ legal-RAG 베스트 프랙티스와 정확히 일치 |
| **JSONB 정본 + 별도 벡터** (D54) | 원문저장 ⟂ 임베딩저장 분리 | 표준(원문=정확 조회, 벡터=검색) | ✅ 정합 |
| **그라운딩/source_id** (D08) | fail-closed + 주입 source_id만 인용 | **RAG가 검색된 법령만 인용하도록 제약 = 인용 정확도 경로**(모델 스케일링보다 신뢰↑) | ✅ 방향 일치 |
| **@effectiveDate 버전** (D43) | 시행일로 판 못박음 | legal-RAG의 **알려진 실패 = anachronistic citation**(옛/틀린 판 인용, 구조적 불완전) | ✅ 특유 강점 — 버전 앵커로 구조적 회피 |

> **핵심:** 상용 legal RAG도 그라운딩에도 불구하고 여전히 환각하고, 특히 *틀린 버전 인용*이 알려진 실패다. 우리 `@effectiveDate` + fail-closed + source_id 실재성 검증이 이 지점을 정조준한다.

## C. 남들이 더 가는 곳 (재검토/후속 후보)

- **하이브리드 검색**(vector + BM25) — 법률 용어는 정확 매칭 유리해 legal-RAG에서 큰 이득. Weaviate 내장. **우리는 post-MVP**(pgvector + tsvector로 구현 가능).
- **리랭킹**(cross-encoder) — 이미 [[rag-evaluation-framework]] config 노브(D53 스윕).
- **Summary-Augmented Chunking** — 우리 요약/LawFacts 벡터 색인(D55)과 정합(값싼 개선으로 확인됨).
- **GraphRAG / 인용 그래프** — 인용 정확도에 법령 인용 그래프를 쓰는 연구. 우리는 source_id 직접. **대안 아키텍처**(post-MVP 후보).

## 결론

적재 3종 결정은 **2025–26 legal-RAG / 벡터-DB 컨센서스와 정합**하고, 특유 강점(**버전 앵커로 anachronism 회피**, fail-closed 그라운딩)은 상용 도구의 알려진 실패를 정조준한다. 내부 정합성은 견고하되 **2개 플래그(차원 커플링·RAGIndexer 시퀀싱)**를 스펙에 못박는 게 좋다.

## Sources
- [Reliable Retrieval for Legal RAG (arXiv 2510.06999)](https://arxiv.org/html/2510.06999v1)
- [Citation Grounding via Legal Citation Graphs (arXiv 2606.00898)](https://arxiv.org/pdf/2606.00898)
- [Vector DB Comparison 2026 (Week One Labs)](https://weekonelabs.com/blog/vector-database-comparison-2026)
- [pgvector vs Pinecone/Qdrant/Weaviate (NisAI)](https://nisai.dev/guides/vector-databases-compared-2026/)
- [7 Chunking Strategies (F22 Labs)](https://www.f22labs.com/blogs/7-chunking-strategies-in-rag-you-need-to-know/)
- [Best Chunking Strategies 2026 (Firecrawl)](https://www.firecrawl.dev/blog/best-chunking-strategies-rag)
