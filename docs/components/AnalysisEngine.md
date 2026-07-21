---
title: AnalysisEngine — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, llm, rag]
related: ["components/component-specs.md", "prompts/analysis-prompt-spec.md", "architecture/v0.5-bill-discovery.md"]
---

# AnalysisEngine (Python, 해석)

> **런타임 변경(D35):** 구현 런타임이 Python → **Spring(Boot 4.0 + Spring AI 2.0)** 으로 통합됨([[v0.6-spring-consolidation|v0.6]] · [[spring-migration|버전 변경점]]). 본 문서의 역할·입출력·동작·결정 의도는 그대로 유효하며, Python 인터페이스 초안은 **포팅 사양**으로 유지된다.


> **쿼리 임베딩+RAG 검색 → 프롬프트 빌드 → 외부 foundation API(Opus) 추론 → 1차 인용검증.** 관련: [[component-specs]] §4 #11·§3 · [[analysis-prompt-spec]] · [[v0.5-bill-discovery]] §3.4

## 역할
알려진(해소된) 법안에 대해 커맨드별 분석을 생성한다. RAG로 현행법 근거를 끌어오고, Opus 4.8을 호출해 **구조화 `ImpactResult`** 를 반환한다. 자체/경량 학습 없음.

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `{ command, bill, persona?, options{prompt_version,layer,max_tokens} }` | [[component-specs]] §3.1 요청 |
| 출력 | `{ result: ImpactResult, injected_source_ids[] }` | §3.1 응답(검증 입력 포함) |

## 파라미터 (설정)
| 파라미터 | 기본 | 설명 |
|---|---|---|
| `reasoning_model` | `claude-opus-4-8` | adaptive thinking, effort=high |
| `embedder` | 공유 [[Embedder]] | `mode="query"` |
| `retrieve_top_k` | 5~ | 현행법 근거 검색 수 |
| `max_tokens` | 4000 | 구조화 JSON 출력 |
| `regen_max` | N | 인용검증 실패 시 재생성 횟수 |

## 동작
1. (필요 시) 쿼리 임베딩 [[Embedder]] `mode="query"` → Vector Index **분석용(현행법)** 검색
2. `source_id` 부여한 컨텍스트 조립([[analysis-prompt-spec]] §3 템플릿) — 조문·diff·(Layer B면)persona
3. **Opus API 호출**(constrained JSON, [[analysis-prompt-spec]] §4 스키마)
4. **1차 인용검증**(스키마 + 인용 존재성), 실패 시 재생성(≤`regen_max`)
5. 산출: `ImpactResult` + `injected_source_ids`(2차 게이트용); BillFacts(Layer A)·결과를 Bill Store 캐시

## 2계층
- **Layer A**(페르소나 무관): 법안 사실·diff·`BillFacts` 생성 → 캐시. `ImpactSummary`/`LawDiff`.
- **Layer B**(페르소나별): A를 입력으로 `PersonaImpact`/`ActionPlan`.

## 인터페이스 (Python 초안)
```python
class AnalysisEngine:
    def analyze(self, req: AnalyzeRequest) -> AnalyzeResponse: ...
    def _retrieve(self, bill) -> list[SourceBlock]: ...   # query embed → law ns
    def _build_prompt(self, ctx) -> Prompt: ...
    def _verify_citations(self, result, injected_ids) -> bool: ...
```

## 구조 결정 의도 (왜 이렇게)
- **쿼리 임베딩은 여기서**(검색용), 코퍼스 임베딩은 [[RAGIndexer]](적재용) — 같은 [[Embedder]]로 벡터공간 일치.
- **추론은 외부 API(Opus).** 임베딩·추론을 분리(별 벤더). 프롬프트/모델은 `prompt_version`·`meta`로 교체 가능.
- **2계층.** 사실층(A)을 페르소나 무관으로 캐시 → 페르소나 N개를 저렴하게(비용 레버, [[decision-log|D07]]).
- **인용 게이트 내장(1차)** + 오케스트레이터 2차(#12). `injected_source_ids`를 함께 반환해 2차 검증을 가능케 함([[component-specs]] §3.1).
- **REST 경계.** Spring Pipeline(#8)이 호출, 결과 반환 — Python↔Spring 계약([[component-specs]] §3).

## 의존 / 관련
- 의존: [[Embedder]](query), Vector Index(현행법), Opus API, Bill Store(캐시)
- 호출자: AnalysisPipeline(Spring, #8) · 규약: [[analysis-prompt-spec]]
