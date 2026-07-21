---
title: Embedder — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, embedding]
related: ["reference/embedding-benchmark.md", "components/component-specs.md", "adr/decision-log.md"]
---

# Embedder (Python, 공유)

> **런타임 변경(D35):** 구현 런타임이 Python → **Spring(Boot 4.0 + Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]] · [[spring-migration|버전 변경점]]). 본 문서의 역할·입출력·동작·결정 의도는 그대로 유효하며, Python 인터페이스 초안은 **포팅 사양**으로 유지된다.


> 텍스트 → 벡터. **적재·검색이 공유**하는 외부 임베딩 API 추상화. 벤더 교체 지점(벤치 대상). 관련: [[embedding-benchmark]] · [[component-specs]] §3.3 · [[decision-log|D32·D33]]

## 역할
RAG Indexer(적재)·Analysis Engine·SourceAnalyzer(검색)가 **동일 모델**로 임베딩하도록 단일 인터페이스 제공. 외부 API만 사용(자체 호스팅 X, [[decision-log|D32]]).

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `texts: list[str]`, `mode: "passage"\|"query"` | 적재=passage, 검색=query |
| 출력 | `list[Vector]` (`dim` 고정) | 코사인용 정규화 벡터 |

## 파라미터 (설정)
| 파라미터 | 예 | 설명 |
|---|---|---|
| `provider` | openai \| upstage | **벤치로 확정**(현재 1536 기준) |
| `model` | `text-embedding-3-small` / `solar-embedding-1-large` | |
| `dim` | 1536 / 4096 | 모델 고정값 |
| `batch_size` | 64~ | 적재 대량 처리 |
| `max_input_tokens` | 모델별 | 긴 조문 분할 기준 |

## 동작
1. `mode`에 맞게 호출 — OpenAI=대칭(모드 무시), Upstage/Cohere/Voyage=query/passage 분리(엔드포인트/`input_type`/prefix)
2. 배치 호출 + 레이트리밋·재시도
3. (필요 시) 정규화 → 코사인 거리용

## 인터페이스 (Python 초안)
```python
class Embedder(ABC):
    dim: int
    @abstractmethod
    def embed(self, texts: list[str], mode: Literal["passage","query"]) -> list[Vector]: ...

class OpenAIEmbedder(Embedder):    # text-embedding-3-small, dim=1536, 대칭
class UpstageEmbedder(Embedder):   # solar-embedding, dim=4096, query/passage 분리
```

## 구조 결정 의도 (왜 이렇게)
- **적재·검색 동일 모델 강제.** 가장 흔한 RAG 버그(인덱스↔쿼리 모델 불일치 → 검색 붕괴)를 *공유 인터페이스*로 구조적으로 차단.
- **벤더 교체 지점.** OpenAI↔Upstage를 같은 인터페이스로 갈아끼워 [[embedding-benchmark|벤치]]를 통제변인 1개로 수행. 벤치 후 `provider` 확정.
- **`mode` 1급 파라미터.** query/document 분리 모델의 정확도 이득을 살리되, 대칭 모델(OpenAI)에선 무시 → 추상화로 차이 흡수.
- **추론 모델과 별개.** Opus(생성)와 다른 벤더/모델. 모델 변경 시 **전 코퍼스 재색인** 필요하므로 한 번 확정하면 고정.
- 데이터 민감도 낮음(공개 법령·합성 페르소나) → 외부 API 적합.

## 의존 / 관련
- 사용처: [[RAGIndexer]](passage), [[AnalysisEngine]]·[[SourceAnalyzer]](query)
- 저장: Vector Index(pgvector)
- 벤치: [[embedding-benchmark]]
