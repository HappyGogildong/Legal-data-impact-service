---
title: Embedder — 클래스 스펙 (설계 확정 · 구현 착수)
status: Draft
date: 2026-08-27
tags: [component, pipeline, embedding]
related: ["reference/embedding-benchmark.md", "components/component-specs.md", "components/store/LawStore.md", "components/rag/RAGIndexer.md", "adr/decision-log.md"]
---

# Embedder

> 텍스트 → 벡터. **적재·검색이 공유**하는 외부 임베딩 API 추상화. Spring AI `EmbeddingModel` 위임. 벤치·차원: [[embedding-benchmark]] · [[decision-log|D32·D33]].
>
> **구현 순서 (2026-08-27 확정):** **포트 + OpenAI 구현체 우선**. Upstage는 D33 벤치 시 같은 포트로 추가(통제변인 = 모델 하나만 교체). 신뢰성은 **Spring AI에 위임(얕게)** — 자체 재시도·배치 분할 없음(D48 측정 선행).
>
> **역할 범위 (2026-08-27 정정):** RAG 적재·검색 **핫패스에는 Embedder가 없다.** [[ChunkStore]]의 `PgVectorStore`가 `add`/`similaritySearch` 시 `EmbeddingModel`로 **직접 내부 임베딩**하기 때문이다. Embedder 포트의 실역할 = ① **모델·dim 고정**(EmbeddingProperties, PgVectorStore 설정과 정합) ② **원시 임베딩 유틸** — [[rag-evaluation-framework|eval Retriever]]·수동 유사도 등 PgVectorStore 밖에서 벡터가 필요한 곳. 벤더 확정(D33)은 Embedder와 PgVectorStore가 **같은 `EmbeddingModel` 빈**을 공유하므로 한 곳에서 바뀐다.

## Responsibility
- **담당:** 텍스트(들) → 고정 차원 벡터. `passage`/`query` 모드 처리. **벤더 교체 지점**(단일 인터페이스).
- **담당 안 함:** **청킹**(무엇을 임베딩할지 = [[RAGIndexer]]/적재 파이프라인) · 벡터 저장([[LawStore]] `PgVectorStore`) · 검색 전략(SourceAnalyzer/RAG) · 생성 추론(Opus — 별개 벤더) · **재시도·배치 분할**(Spring AI/호출자 책임, 아래 Behavior).

## Collaborators
- **Spring AI `EmbeddingModel`**(OpenAI 구현 위임) — 실제 API 호출·기본 재시도.
- 사용처: [[RAGIndexer]](passage, 적재) · [[AnalysisEngine]]·[[SourceAnalyzer]](query, 검색).
- 외부 시스템: **외부 임베딩 API**(현재 OpenAI). 자체 호스팅 없음(D32).

## 구조 (포트 + 구현)
| 타입 | 역할 |
|---|---|
| `Embedder` (인터페이스) | 벤더 교체 지점. `dim()` · `embed(text, mode)` · `embed(texts, mode)`. |
| `OpenAiEmbedder` (POJO, `@Bean` in `PipelineConfig`) | Spring AI `EmbeddingModel` 위임 구현체. mode 흡수·정규화·dim 가드. 프레임워크 비의존(생성자 주입). 계측은 Spring AI 내장(gen_ai.*)에 위임 — 자체 span 없음. |
| `EmbeddingProperties` (`@ConfigurationProperties("lia.embedding")`) | **dim(기본 1536) 단일 소스** — [[LawStore]] `chunks`·[[RAGIndexer]]가 같은 값을 참조해 dim 불변식을 한 곳에서 강제. |
| `FakeEmbedder` (test) | 결정론 벡터(해시 기반). 실 API 없이 계약 검증 + (후속) [[rag-evaluation-framework]] 하네스 재사용. |

> 패키지: `com.lia.core.pipeline.embed` — 적재(index)·검색 양쪽이 공유하는 교차 서비스라 `pipeline/index`(적재 전용) 대신 별도 패키지.

## Contract
- `dim() → int` — 고정 차원(1536, `EmbeddingProperties`).
- `embed(text, mode) → float[]` — 단건 편의.
- `embed(texts, mode) → List<float[]>` — 배치. `mode ∈ {PASSAGE, QUERY}`.
  - **전제:** 각 텍스트가 모델 `max_input_tokens` 이내(초과분 분할은 **호출자**=RAGIndexer 책임).
  - **보장:** 입력 **순서대로** `dim` 벡터 · **코사인용 단위 정규화** · **적재·검색이 같은 모델**(인터페이스가 강제).

## Behavior (구현 결정)
- **Mode 흡수** — OpenAI `text-embedding-3-small`은 **대칭 모델**이라 `OpenAiEmbedder`는 `PASSAGE`/`QUERY`를 **무시**(첫 호출 시 debug 로그 1회). `Mode`는 1급으로 남겨, 나중 Upstage(`-query`/`-passage`)가 모델 스위칭에 사용 — 추상화가 벤더 차이를 흡수.
- **방어적 정규화** — OpenAI는 이미 단위벡터를 반환하지만 **공유 헬퍼로 L2 정규화**를 한 번 더 태워 **벤더 무관하게** 코사인 불변식을 보장(비용 무시 수준).
- **dim 가드** — 첫 응답 벡터 길이가 `dim()`과 다르면 예외 → 모델·설정 불일치를 조기 발견(색인 후 붕괴 방지).
- **재시도·배치 분할 없음(얕게)** — `embed`는 `EmbeddingModel`에 위임하고 순서·dim·정규화만 보장한다. 레이트리밋/일시 오류 재시도는 **Spring AI 기본**(`spring.ai.retry`), 과대 입력 분할은 **호출자(RAGIndexer)**. 자체 배치·백오프는 **캐시/관측 단계에서 지표로 병목을 증명한 뒤** 추가(D48 — speculative 최적화 배제).

## External API Contract
| 벤더 | 모델 | dim | 모드 | 상태 |
|---|---|---|---|---|
| **OpenAI** | `text-embedding-3-small` | 1536 | 대칭(모드 무시) | **구현 착수(기준)** |
| Upstage | `solar-embedding-1-large`(`-query`/`-passage`) | 4096 | 분리(한국어 특화) | 벤치 시 동일 포트로 추가(D33) |

- 벤더 최종 확정은 **[[embedding-benchmark|벤치]](Recall@5·MRR, D33)** — 현재 미확정. 교체는 `provider`/`base-url` 설정 + 같은 포트 구현체 추가(Upstage는 OpenAI-호환 base-url 오버라이드).
- 배선: `build.gradle` `spring-ai-starter-model-openai` + `application.yml` `spring.ai.openai.embedding`(모델·키). dim은 `lia.embedding.dim`.

## Invariants
- **적재·검색 동일 모델·동일 dim** — 인덱스↔쿼리 모델 불일치(가장 흔한 RAG 붕괴)를 인터페이스로 구조적 차단.
- `dim`은 모델 고정값이자 [[LawStore]] `chunks`(pgvector) 차원과 일치해야 한다(불일치 시 색인 불가) → `EmbeddingProperties` 단일 소스.
- 반환 벡터는 코사인 거리용 단위 정규화(방어적).

## Error Handling
- Spring AI `EmbeddingModel` 예외를 **그대로 상위로 전파**(자체 래핑 없음). 레이트리밋/일시 오류 재시도는 Spring AI 기본. 지속 실패 → 적재 배치·질의 경로가 처리.
- 설정 오류(dim 불일치)는 **가드에서 즉시 예외** — 조용한 오염 대신 조기 실패.

## Side Effects
- **외부 API 호출**(네트워크·비용). 저장·상태 변경 없음(벡터 반환만).

## Observability
- **Spring AI 내장 계측에 위임** — `OpenAiEmbeddingModel`은 ObservationRegistry 빈이 있으면 자동으로 `gen_ai.client.operation`(타이머+span, OTel GenAI 컨벤션: `gen_ai.system`·`gen_ai.request.model`·`gen_ai.request.embedding.dimensions`·`gen_ai.usage.input_tokens`/`total_tokens`) + `gen_ai.client.token.usage`를 낸다. 벤치의 지연(p50/p95)·토큰·차원이 여기서 잡힌다([[embedding-benchmark]] §2).
- **`lia.embed`를 두지 않는 이유:** 우리가 `embed()`를 다시 `observe`로 감싸면 `gen_ai.*` 위의 이중 스팬일 뿐이고, 정규화 오버헤드(µs급) 외엔 새 정보가 없다. normalize/diff가 `lia.*`를 유지하는 것은 그쪽이 외부계측이 없는 순수 CPU 단계라서다(임베딩은 Spring AI가 이미 계측). 지원 벤더 = OpenAI·Mistral·Ollama이며 Upstage도 OpenAI-호환 클라이언트라 동일 적용.
- **대시보드 주의:** 임베딩 지표는 `lia.*`가 아니라 `gen_ai_client_operation_seconds*`에 있다 — Grafana에 포함할 것.

## 검증
- **단위(Fake):** `FakeEmbedder`로 순서·dim·정규화 계약. `OpenAiEmbedder`는 `EmbeddingModel` 목(mock)으로 mode 무시·dim 가드 검증. 기본 `./gradlew test`에서 **실 API 호출 없음**.
- **라이브 스모크(수동):** **명시적 옵트인** `LIA_EMBED_LIVE=1` + `OPENAI_API_KEY` 둘 다 있을 때만 실행 — 키가 있어도 기본 `./gradlew test`에서는 **스킵**(전체 실행·CI의 우발적 과금 방지). 실 호출로 dim=1536·정규화 실측. **비용이 나므로 사용자가 직접 실행**: `LIA_EMBED_LIVE=1 ./gradlew test --tests "*EmbedderLiveSmokeTest"`.

## Design Constraints
- **외부 API only**(D32) — 인프라 예산 없음, 공개 법령이라 데이터 민감도 낮아 적합.
- **추론 모델과 별개** — Opus(생성)와 다른 벤더. **모델 변경 = 전 코퍼스 재색인** → 한 번 확정하면 고정. 그래서 코드는 포트로 벤더에 비결합.
- `mode` 1급 — query/passage 분리 모델의 정확도 이득을 살리되 대칭 모델(OpenAI)에선 흡수.

## 관련
- 벤치·벤더 확정: [[embedding-benchmark]](D33) · 저장: [[LawStore]] `chunks` · 청킹 정책(변경 조문 단위 + 요약 벡터)은 [[RAGIndexer]](D55).
