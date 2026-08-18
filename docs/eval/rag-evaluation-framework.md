---
title: RAG 평가·회귀 프레임워크
status: Draft
version: 0.1
date: 2026-08-18
tags: [eval, rag, regression, testing, quality]
related:
  - "reference/embedding-benchmark.md"
  - "prompts/analysis-prompt-spec.md"
  - "mvp/components-io-and-scope.md"
  - "adr/decision-log.md"
---

# RAG 평가·회귀 프레임워크

**관련:** [[embedding-benchmark|임베딩 벤치(D33)]] · [[analysis-prompt-spec|프롬프트 정의서]] §6 · [[components-io-and-scope|Evaluation Harness(D36)]] · [[decision-log|D53]]

## 0. 목적

RAG 구성(**chunk size · embedding · top-k · reranker · query rewriting · prompt**)을 바꿀 때 **기존 질문셋 성능이 떨어지는지 자동 감지**한다. RAG가 틀렸을 때 **검색 문제인지 생성 문제인지 분리**할 수 있어야 한다.

기존에 흩어져 있던 셋을 하나로 묶는다: 검색 평가(D33), E2E 하네스(D36), 검증 훅(§6).

---

## 1. 3층 구조

```
                RAG Evaluation
        ┌────────────┴────────────┐
     Retrieval                 Generation(Answer)
     Recall@K·Hit@K            Faithfulness·Relevance·Correctness
     MRR·nDCG                        │
        └────────────┬────────────┘
                End-to-End
             Task Success
        ┌────────────┴────────────┐
     Answerable 질문셋        Unanswerable 질문셋
     (근거 있는 답)           (반드시 거부 = fail-closed)
```

- **Retrieval** — 검색이 정답 조문을 상위에 올리는가.
- **Answer** — 생성된 답이 근거에 충실(Faithfulness)하고, 질문에 맞고(Relevance), 옳은가(Correctness).
- **E2E** — 전체 파이프라인의 과업 성공. **Answerable은 근거 있는 답**, **Unanswerable은 올바른 거부**.

---

## 2. 판정 정책 — 결정론 게이트 + 보조 (도구 하이브리드)

| 층 | 지표 | 도구 | 성격 |
|---|---|---|---|
| Retrieval | Recall@K · Hit@K · MRR · nDCG | **Java** | 결정론 **게이트** |
| Answer(faithfulness) | 인용 존재성(claims.citations ⊆ 주입 source_id) | **Java** | 결정론 **게이트** |
| E2E(unanswerable) | 거부 정확도(state ∈ {NOT_FOUND_YET, UNVERIFIED}) | **Java** | 결정론 **게이트** |
| Answer(품질) | Answer Relevance · Correctness · 인용 지지 | **RAGAS/Python** `tools/eval` | LLM-judge **보조**(오프라인) |

**규율:**
- **LLM을 게이트 판정자로 쓰지 않는다**(D14 "정답판정 금지"). 게이트는 전부 결정론 수치 비교라 CI에서 재현된다.
- LLM-judge·사람검수는 **보조**(비게이트) — 소프트 품질의 신호일 뿐, 사람 스팟체크로 진실을 확인한다.

### 왜 이 분리인가 (핵심 두 통찰)

**① Faithfulness가 반쯤 공짜다.** RAGAS의 대표 지표 Faithfulness조차, 우리는 이미 **결정론 버전**을 갖는다 — 그라운딩 규율상 "모든 주장은 주입된 `source_id`만 인용, 없으면 무효"(§6.2). 이걸 게이트로 쓰면 **환각 인용이 CI를 실패**시킨다. "인용이 주장을 *지지*하는가"(§6.3)만 LLM-judge 보조로 남긴다.

**② Unanswerable = 안전 게이트.** fail-closed 설계라 조작·이미 시행 중·비법령([[QueryPlanner]] S8/S9)에는 **반드시 거부**해야 한다. 대부분의 RAG 평가는 이 축이 약한데, 법률 서비스엔 **환각 안 함 = 정답**이고 이는 결정론으로 측정된다. **RAG 없이 지금 실제 `SourceAnalyzer`로 가동**된다.

---

## 3. 골든셋 전략 (레포에 버전 고정)

| 종류 | 형식 | 확보 방법 |
|---|---|---|
| **Retrieval** | `{query, expectedSourceIds[]}` | 법 구조에서 **반자동**(예: "제N조 뭐 바뀌어?" → `LAW:{id}@{efYd}:art:N`) |
| **Unanswerable** | `{query, expectedState}` | 조작 법명 · 이미 시행 중 · 비법령(S8/S9) |
| **Answer** | `{query, referenceAnswer, requiredCitations}` | **소량 사람검수**(D36). 보조층 — 포맷만 두고 RAGAS/사람이 소비 |

픽스처: `core/src/main/resources/eval/*.json`.

---

## 4. 회귀 테스트 (통제변인)

1. **한 번에 하나만 바꾼다**(D33 §3) — `RagConfig`의 노브 하나(top-k·reranker·…)만 변경.
2. 같은 골든셋으로 돌려 `EvalReport`(지표 맵) 산출.
3. **baseline 대비 검사** — 허용오차보다 더 떨어지면 **회귀 실패**.
4. **절대 임계** — `Recall@5 ≥ 0.80`(D33), `거부 정확도 = 1.0`·`faithfulness = 1.0`(안전) 미달 시 실패.

→ "reranker를 켰더니 Recall@5가 0.82→0.71로 떨어졌다"가 **자동으로 CI 실패**로 드러난다.

---

## 5. 구현 매핑 (`com.lia.core.eval`)

| 클래스 | 역할 | 상태 |
|---|---|---|
| `RagConfig` | 노브(통제변인) + baseline 키 | ✅ |
| `GoldenSet` | `RetrievalCase`·`RefusalCase` + JSON 로더 | ✅ |
| `RetrievalMetrics` | hit@k·recall@k·mrr·ndcg (순수) | ✅ |
| `FaithfulnessGate` | 인용 존재성(§6.2) | ✅ |
| `RefusalMetric` | 거부 정확도(fail-closed) — 실 `SourceAnalyzer` 가동 | ✅ |
| `RagEvalRunner` | config+골든+`Retriever` → `EvalReport` | ✅ |
| `RegressionGate` | baseline·임계 비교 → 통과/실패 | ✅ |
| `Retriever`(포트) | 검색 격리 — 지금 `FakeRetriever`, RAG landing 시 벡터스토어로 교체 | ✅ 포트 |

단위 테스트: 합성 랭킹·citations로 지표 수학·게이트 로직 검증(RAG 불필요).

---

## 6. 로드맵

**근term(RAG와 함께):** `Retriever` 포트를 실제 벡터 검색으로 교체 → 실 골든셋으로 retrieval 게이트 가동. 거부 게이트는 이미 가동 가능. config 스윕으로 임베딩 벤더(D33)·top-k·청킹 확정.

**post-MVP(D36):** RAGAS/Python 답변품질(Relevance·Correctness·인용 지지) · 합성 페르소나 E2E · 사람검수 correctness 골든셋 · CI(GitHub Actions) 통합·baseline 자동 갱신.

> 이 프레임워크는 D33(검색 평가)를 흡수하고, D36(E2E 하네스)의 결정론 부분(retrieval·refusal 게이트)을 근term으로 당긴다. 답변품질·페르소나 E2E는 D36대로 post-MVP.
