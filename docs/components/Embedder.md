---
title: Embedder — 클래스 스펙 (spec-first)
status: Draft
date: 2026-08-22
tags: [component, pipeline, embedding]
related: ["reference/embedding-benchmark.md", "components/component-specs.md", "components/LawStore.md", "components/RAGIndexer.md", "adr/decision-log.md"]
---

# Embedder

> 텍스트 → 벡터. **적재·검색이 공유**하는 외부 임베딩 API 추상화. **구현 전 계약(spec-first)** — Spring AI `EmbeddingModel` 위임. 벤치·차원: [[embedding-benchmark]] · [[decision-log|D32·D33]].

## Responsibility
- **담당:** 텍스트(들) → 고정 차원 벡터. `passage`/`query` 모드 처리. 배치·레이트리밋·재시도. **벤더 교체 지점**(단일 인터페이스).
- **담당 안 함:** **청킹**(무엇을 임베딩할지 = [[RAGIndexer]]/적재 파이프라인) · 벡터 저장([[LawStore]] `PgVectorStore`) · 검색 전략(SourceAnalyzer/RAG) · 생성 추론(Opus — 별개 벤더).

## Collaborators
- **Spring AI `EmbeddingModel`**(OpenAI/Upstage 위임) — 실제 API 호출.
- 사용처: [[RAGIndexer]](passage, 적재) · [[AnalysisEngine]]·[[SourceAnalyzer]](query, 검색).
- 외부 시스템: **외부 임베딩 API**(OpenAI 또는 Upstage). 자체 호스팅 없음(D32).

## Contract
- `dim() → int` — 고정 차원(1536).
- `embed(texts, mode) → List<float[]>` — `mode ∈ {PASSAGE, QUERY}`.
  - **전제:** 각 텍스트가 모델 `max_input_tokens` 이내(초과분 분할은 **호출자**=RAGIndexer 책임).
  - **보장:** 입력 순서대로 `dim` 벡터. 코사인용 정규화. **적재·검색이 같은 모델**을 쓴다(인터페이스가 강제).

## External API Contract
| 벤더 | 모델 | dim | 모드 |
|---|---|---|---|
| **OpenAI** | `text-embedding-3-small` | 1536 | 대칭(모드 무시) |
| **Upstage** | `solar-embedding-1-large`(`-query`/`-passage`) | 4096 | 분리(한국어 특화) |

- 벤더는 **[[embedding-benchmark|벤치]]로 확정**(Recall@5·MRR, D33) — 현재 미확정. 교체는 `provider`/`base-url` 설정만(Upstage는 OpenAI-호환 base-url 오버라이드).
- 파라미터: `provider` · `model` · `dim` · `batch-size`(적재 대량) · `max-input-tokens`(분할 기준).

## Invariants
- **적재·검색 동일 모델·동일 dim** — 인덱스↔쿼리 모델 불일치(가장 흔한 RAG 붕괴)를 인터페이스로 구조적 차단.
- `dim`은 모델 고정값이자 [[LawStore]] `chunks`(pgvector) 차원과 일치해야 한다(불일치 시 색인 불가).
- 벡터는 코사인 거리용으로 정규화.

## Error Handling
- 레이트리밋/일시 오류 → 백오프 재시도. 지속 실패 → 상위(적재 배치·질의 경로)로 전파.

## Side Effects
- **외부 API 호출**(네트워크·비용). 저장·상태 변경 없음(벡터 반환만).

## Design Constraints
- **외부 API only**(D32) — 인프라 예산 없음, 공개 법령이라 데이터 민감도 낮아 적합.
- **추론 모델과 별개** — Opus(생성)와 다른 벤더. **모델 변경 = 전 코퍼스 재색인** 필요 → 한 번 확정하면 고정.
- `mode` 1급 — query/passage 분리 모델의 정확도 이득을 살리되 대칭 모델(OpenAI)에선 무시(추상화가 흡수).

## 관련
- 벤치·벤더 확정: [[embedding-benchmark]](D33) · 저장: [[LawStore]] `chunks` · 청킹 정책(조문 단위 vs 슬라이딩)은 [[RAGIndexer]] 결정(D33 §Open).
