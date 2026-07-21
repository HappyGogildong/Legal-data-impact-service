---
title: RAGIndexer — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, rag, indexing]
related: ["components/component-specs.md", "architecture/v0.5-bill-discovery.md", "reference/embedding-benchmark.md"]
---

# RAGIndexer (Python, 적재)

> **런타임 변경(D35):** 구현 런타임이 Python → **Spring(Boot 4.0 + Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]] · [[spring-migration|버전 변경점]]). 본 문서의 역할·입출력·동작·결정 의도는 그대로 유효하며, Python 인터페이스 초안은 **포팅 사양**으로 유지된다.


> 코퍼스를 임베딩해 **Vector Index에 적재**. 두 네임스페이스: *분석용(현행법·선례)* + *탐색용(법안 요약·BillFacts)*. 관련: [[component-specs]] §4 #4 · [[v0.5-bill-discovery]] §3.4

## 역할
검색 *전에* 코퍼스를 벡터화해 둔다(오프라인/수집 시점). Analysis Engine은 검색만, Indexer는 적재만 — 책임 분리.

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력(분석용) | `RawLaw`/현행 조문 | [[SourceConnector|LawConnector]] 산출 |
| 입력(탐색용) | 법안 **요약·BillFacts·affectedDomains** | [[Normalizer]]·[[AnalysisEngine]] 산출 |
| 출력 | Vector Index 엔트리(벡터 + `source_id` 메타) | pgvector upsert |

## 파라미터 (설정)
| 파라미터 | 예 | 설명 |
|---|---|---|
| `namespace` | `law` \| `bill` | 분석용/탐색용 분리 |
| `chunk_policy` | 조문 단위(기본) | 현행법=조문, 법안=요약 1~3벡터 |
| `embedder` | 공유 [[Embedder]] | `mode="passage"` |
| `batch_size` | 64~ | 대량 적재 |

## 동작
1. 코퍼스 → 청크(현행법=조문 단위, 법안=요약/BillFacts)
2. `source_id` 부여: `LAW:{lawId}:art:{no}` / `BILL:{billNo}`(탐색용)
3. [[Embedder]] `mode="passage"`로 임베딩
4. pgvector에 upsert(네임스페이스 + `source_id` 메타 동반)
5. 증분: `revision` 변동분만 재적재

## 인터페이스 (Python 초안)
```python
class RAGIndexer:
    def index_law(self, articles: list[LawArticle]) -> None: ...   # namespace="law"
    def index_bill(self, bill: Bill, facts: BillFacts) -> None: ... # namespace="bill"
```

## 구조 결정 의도 (왜 이렇게)
- **적재 ≠ 검색.** 임베딩 적재를 Analysis Engine에서 분리(v0.3→v0.4의 핵심 교정, [[decision-log|D29]]). 적재는 배치·무상태, 검색은 런타임.
- **두 네임스페이스.** *분석용(현행법)* 과 *탐색용(법안)* 은 목적이 달라 분리 — 같은 인덱스에 섞으면 검색 노이즈([[decision-log|D30]]).
- **법안 전문은 임베딩하지 않음.** 법안 한 건은 분석 시 컨텍스트에 통째로 들어가므로, 탐색용은 *요약/BillFacts*만(저장·노이즈 절감).
- **`source_id` 동반 적재.** 검색 결과가 곧 인용 근거가 되도록(그라운딩 게이트 입력).
- **동일 임베딩 모델**([[Embedder]] 공유)로 검색과 벡터공간 일치.

## 의존 / 관련
- 의존: [[Embedder]](passage), Vector Index(pgvector)
- 입력: [[SourceConnector]](현행법), [[Normalizer]]/[[AnalysisEngine]](법안 요약·BillFacts)
- 벤치: [[embedding-benchmark]](적재→검색 정확도)
