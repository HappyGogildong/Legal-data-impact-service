---
title: RAGIndexer — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, rag, indexing]
related: ["components/component-specs.md", "architecture/v0.8-pending-law-corpus.md", "reference/embedding-benchmark.md"]
---

# RAGIndexer (Spring, 적재)

> **v0.2 (D42·D44, 2026-08-02):** 탐색용 네임스페이스가 `bill` → **`pending`** 으로 바뀌었고, 적재 대상이 *법안 요약* → **시행예정 법령 요약**이 됐다. 런타임은 Spring(D35).

> 코퍼스를 임베딩해 **Vector Index에 적재**. 두 네임스페이스: *분석용(시행중 법령 조문)* + *탐색용(시행예정 법령 요약·LawFacts)*. 관련: [[component-specs]] §4 #4 · [[v0.8-pending-law-corpus]] §4.5

## 역할
검색 *전에* 코퍼스를 벡터화해 둔다(오프라인/수집 시점). Analysis Engine은 검색만, Indexer는 적재만 — 책임 분리.

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력(분석용) | 시행중 법령 조문(`Law.articles`) | [[SourceConnector|LawConnector]] `fetchCurrent` → [[Normalizer]] |
| 입력(탐색용) | 시행예정 법령 **요약·LawFacts·affectedDomains** | [[Normalizer]]·Layer A 파생 산출 |
| 출력 | Vector Index 엔트리(벡터 + `source_id` 메타) | pgvector upsert |

## 파라미터 (설정)
| 파라미터 | 예 | 설명 |
|---|---|---|
| `namespace` | `law` \| `pending` | 분석용(시행중)/탐색용(시행예정) 분리 |
| `chunk_policy` | 조문 단위(기본) | 시행중=조문, 시행예정=요약 1~3벡터 |
| `embedder` | 공유 [[Embedder]] | `mode="passage"` |
| `batch_size` | 64~ | 대량 적재 |

## 동작
1. 코퍼스 → 청크(시행중 법령=조문 단위, 시행예정 법령=요약/LawFacts)
2. `source_id` 부여: `LAW:{lawId}:art:{no}`(분석용) / `LAW:{lawId}@{effectiveDate}`(탐색용)
   ⚠️ 탐색용은 **시행일을 포함**한다 — 같은 법령ID에 시행예정본이 복수일 수 있다(D43)
3. [[Embedder]] `mode="passage"`로 임베딩
4. pgvector에 upsert(네임스페이스 + `source_id` 메타 동반)
5. 증분: `revision` 변동분만 재적재

## 인터페이스 (Java, `com.lia.core.pipeline.index`)
```java
public class RagIndexer {                                  // com.lia.core.pipeline.index
    void indexCurrent(Law current);                        // namespace="law"     — 조문 단위
    void indexPending(Law pending, LawFacts facts);        // namespace="pending" — 요약 단위
}
```

## 구조 결정 의도 (왜 이렇게)
- **적재 ≠ 검색.** 임베딩 적재를 Analysis Engine에서 분리(v0.3→v0.4의 핵심 교정, [[decision-log|D29]]). 적재는 배치·무상태, 검색은 런타임.
- **두 네임스페이스.** *분석용(시행중 법령)* 과 *탐색용(시행예정 법령)* 은 목적이 달라 분리 — 같은 인덱스에 섞으면 "현행 조문"과 "곧 바뀔 내용"이 뒤섞여 검색 노이즈가 된다([[decision-log|D30]]).
- **법령 전문은 임베딩하지 않음.** 분석 시 대상 법령은 컨텍스트에 통째로 들어가므로, 탐색용은 *요약/LawFacts*만 넣는다(저장·노이즈 절감). 게다가 변경 조문만 다루면 되므로(실측 137→6) 전문 임베딩의 실익이 더 작다.
- **`source_id` 동반 적재.** 검색 결과가 곧 인용 근거가 되도록(그라운딩 게이트 입력).
- **동일 임베딩 모델**([[Embedder]] 공유)로 검색과 벡터공간 일치.

## 의존 / 관련
- 의존: [[Embedder]](passage), Vector Index(pgvector)
- 입력: [[Normalizer]](시행중 조문 · 시행예정 요약), Layer A 파생(LawFacts)
- 벤치: [[embedding-benchmark]](적재→검색 정확도)
