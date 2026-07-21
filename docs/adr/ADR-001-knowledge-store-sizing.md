---
adr: 1
title: 지식 저장소 구성 — 분리형 vs 통합형(Postgres+pgvector)
status: Proposed
date: 2026-06-22
deciders: LIA 아키텍처 오너
tags: [adr, architecture, storage, cost, rag]
supersedes: null
based_on: "architecture/v0.3-no-video-internal-mcp.md"
applies_to: "architecture/v0.4 (예정)"
related: ["architecture/v0.3-no-video-internal-mcp.md", "ARCHITECTURE.md"]
---

# ADR-001: 지식 저장소 구성 — 분리형(RDB + 전용 벡터DB) vs 통합형(Postgres+pgvector)

**Status:** Proposed
**Date:** 2026-06-22
**Deciders:** LIA 아키텍처 오너
**관련 아키텍처 버전**
- **기반(결정 전):** [[v0.3-no-video-internal-mcp|v0.3]] §2 다이어그램의 `② 지식 저장소(S1/S2)` — 현재 S1/S2를 분리 저장소로 묘사.
- **반영 예정(결정 후):** v0.4 (이 ADR이 `Accepted`되면 S1+S2를 단일 Postgres+pgvector로 합친 새 스냅샷 추가).
- **매핑 출처:** [[ARCHITECTURE]] 의 "관련 결정(ADR)" 표 — 버전↔ADR 연결은 그 표가 단일 출처.

## Context

[[v0.3-no-video-internal-mcp|v0.3]] 다이어그램은 `S1 Bill Store(RDB)`와 `S2 Vector Index(RAG)`를 두 개의 분리된 저장소로 그린다. 실제로 두 개의 매니지드 서비스를 띄울지, 하나로 합칠지 판단하려면 **얼마나 담아야 하는지(용량)**와 **그 결과 월 비용**을 먼저 추산해야 한다.

핵심 결론: 이 서비스의 데이터는 클라우드 기준으로 "작다"(워킹셋 수십 GB). **순수 스토리지 비용은 무시할 수준이고, 진짜 비용 동인은 서빙 인스턴스다.** 따라서 현 단계에서 RDB와 벡터 인덱스를 물리적으로 분리하는 것은 과설계이며, 단일 Postgres+pgvector로 합치는 편이 적합하다.

### 추산 전제 (조정 가능)

| 항목 | 가정 | 근거/비고 |
|---|---|---|
| 대상 법안 수 | **~50,000건** (진행 중 + 최근 2회기), 상한 100,000 | 21대 국회 4년간 발의 ~26,000건. 1회기≈24K~26K |
| 법안당 추출 텍스트 | 평균 **40 KB** | 한글 UTF-8(자당 3B), HWP/PDF 본문 추출 기준 |
| 현행법령 기준선(diff용) | **~5,000건 × 80 KB** | 법률+시행령+시행규칙. *조례(13만+)는 향후 확장으로 제외* |
| 페르소나 | 6종, 결과 캐시 | v0.3 §4.2 |
| 임베딩 | **1536차원 float32**, 청크 ~400토큰(~1.5KB) | text-embedding-3-small 기준 |

> [!warning] 추정 정확도
> 이 수치는 1차 back-of-envelope 추정이다. 실제 의안 원문 평균 크기와 캐시 적중 정책에 따라 ±2배 변동 가능. **조례·국세청 해석례까지 확장하면 코퍼스가 10~100배** 커져 결론이 바뀐다(아래 Consequences 참고).

### 용량 추산

**RDB (Bill Store)**
- 법안 원문+구조화: 50,000 × 50KB ≈ **2.5 GB**
- 현행법령 기준선: 5,000 × 80KB ≈ **0.4 GB**
- ImpactResult 캐시(현실적, 인기 법안 ~10%만 다페르소나 캐시): **1~2 GB**
- 인덱스/오버헤드 ×1.5 → **합계 ≈ 6~10 GB** (설계 헤드룸 50 GB)

**Vector Index (RAG)**
- 청크 수 ≈ 2.9GB ÷ 1.5KB ≈ **~1.5~2M 청크**
- raw 벡터: 2M × 1536 × 4B ≈ **12 GB**
- HNSW 인덱스 + payload(원문) ×1.5~2 → **18~24 GB**
  - 768차원 모델 사용 시 절반, 스칼라/PQ 양자화 시 1/4
- **합계 ≈ 12~24 GB** (설계 헤드룸 50 GB)

**워킹셋 총합 ≈ 20~35 GB, 설계 목표 ~100 GB.**

## Decision

**Option A 채택.** MVP~초기 운영 단계에서 지식 저장소는 **단일 RDS PostgreSQL + pgvector**로 통합한다. v0.3 다이어그램의 S1/S2는 *논리적 분리*로 유지하되 물리적으로는 한 인스턴스에 둔다.

## Options Considered

### Option A: 통합형 — 단일 RDS PostgreSQL + pgvector (권장)
RDB와 벡터 인덱스를 한 Postgres 인스턴스에 둔다. (S1/S2는 논리적으로만 분리)

| Dimension | Assessment |
|---|---|
| Complexity | **Low** — 서비스 1개, 트랜잭션 일관성(법안↔벡터) 자연스러움 |
| Cost | **Low** — 아래 표 ~$70/월 |
| Scalability | ~수백만 벡터까지 HNSW로 충분. 그 이상은 한계 |
| Team familiarity | High — Spring/JPA가 이미 같은 RDB 사용 |

**Pros:** 운영 부담 최소, 비용 최소, 법안 메타데이터와 벡터를 한 쿼리/트랜잭션으로 결합. v0.3의 "세 축 독립 확장" 철학과 충돌 없음.
**Cons:** 코퍼스가 조례까지 확장돼 벡터가 ~10M+로 가면 검색 지연·인덱스 빌드 부담.

### Option B: 분리형 — RDS + Amazon OpenSearch(k-NN)
| Dimension | Assessment |
|---|---|
| Complexity | High — 서비스 2개, 동기화 파이프라인 필요 |
| Cost | **High** — 최소 노드 2개로 진입장벽 큼 |
| Scalability | 매우 높음(수천만+) |
| Team familiarity | Medium |

**Pros:** 대규모·고QPS에 강함. **Cons:** 현 데이터량 대비 명백한 과설계. 최소 구성도 월 $300대.

### Option C: 분리형 — RDS + 매니지드 벡터DB(Qdrant Cloud / Pinecone serverless)
**Pros:** 벡터 검색 특화, serverless면 사용량 과금. **Cons:** 외부 벤더 종속, 동기화 복잡도, RDB 대비 추가 비용. 현 규모엔 pgvector 대비 이점 미미.

## 비용 추산 (AWS ap-northeast-2 서울, 월, 100GB 기준)

| 구성 | 서빙/인스턴스 | 스토리지 | **월 합계** |
|---|---|---|---|
| **A. RDS+pgvector** (Single-AZ db.t4g.medium) | ~$50 | gp3 100GB ~$12 + 백업 ~$5 + S3원문 ~$2 | **≈ $70** |
| A. Multi-AZ(운영 권장) | ~$100 | ~$19 | **≈ $120** |
| **B. RDS+OpenSearch** | RDS ~$50 + OpenSearch 2×m6g.large ~$240 | ~$20 | **≈ $310** |
| **C. RDS+Qdrant/Pinecone** | RDS ~$50 + 벡터DB ~$30~100 | 포함 | **≈ $90~160** |

**순수 스토리지만** 떼어 보면: S3 100GB = **$2.3/월**, gp3 100GB = **$12/월**. 즉 **저장 용량 자체는 비용 문제가 아니다** — 동인은 항상 "띄워 둔 인스턴스"다.

### 참고: 스토리지 밖 비용 (런타임, 별도 추적)
- **최초 임베딩**: ~2M청크 × 400토큰 = 800M 토큰 × $0.02/1M ≈ **$16 (1회성)**
- **증분 임베딩**: 월 신규 법안 수백~수천 건뿐 → 사실상 무시
- **영향분석 LLM 추론**: 쿼리 수에 비례, 저장과 무관 — *실제 운영비의 대부분은 여기*이므로 캐시(v0.3 §6) 효과가 비용에 직결

## Trade-off Analysis

데이터가 수십 GB·벡터 200만 미만인 한, OpenSearch/전용 벡터DB가 주는 확장성은 **지금 필요 없는 능력에 대한 고정비**다. pgvector는 이 구간(수백만 벡터, HNSW)에서 충분한 지연·정확도를 낸다. 분리형의 유일한 실익(독립 스케일, 초고QPS)은 트래픽이 그 수준에 도달했을 때 비로소 발생한다. 따라서 **A → (필요 시) C → B 순의 진화 경로**가 합리적이다.

## Consequences

- **쉬워지는 것:** 운영 서비스 1개, 법안 메타↔벡터 조인/트랜잭션, 비용 예측(월 $70~120).
- **어려워지는 것:** 벡터 검색과 OLTP가 자원을 공유 → 읽기 부하 급증 시 read replica 분리 필요.
- **재검토 트리거(이때 B/C로 이전):**
  1. 벡터 수가 **~5~10M** 초과 (≈ **조례/해석례 확장** 시 거의 확실)
  2. 검색 p95 지연이 SLA 초과, 또는 인덱스 빌드 시간이 운영 윈도우 초과
  3. 벡터 검색 QPS가 OLTP를 압박

## Action Items

1. [ ] 실측 보정: 의안 원문 50~100건 추출해 **평균 텍스트 크기** 측정 → 위 40KB 가정 검증
2. [ ] 임베딩 **차원 결정**(1536 vs 768 한국어 특화) — 용량·정확도 trade-off 벤치
3. [ ] RDS db.t4g.medium + pgvector(HNSW)로 **수직 슬라이스 1건**(v0.3 §7-1) 부하 측정
4. [ ] **캐시 정책 확정** — 런타임 LLM 비용이 실질 운영비이므로 ImpactResult 캐시 적중률이 핵심
5. [ ] 조례 확장 로드맵에 "벡터 저장소 재평가" 게이트 명시
