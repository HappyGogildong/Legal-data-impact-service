---
title: AnalysisEngine — 컴포넌트 설계
status: Draft
date: 2026-06-30
tags: [component, pipeline, llm, rag]
related: ["components/component-specs.md", "prompts/analysis-prompt-spec.md", "architecture/v0.8-pending-law-corpus.md"]
---

# AnalysisEngine (Spring, 해석)

> **v0.2 (D41·D42, 2026-08-02):** 분석 대상이 `Bill` → **`Law`(시행예정)**, 개인화 입력이 *페르소나 세그먼트* → **자기신고 프로필**로 바뀌었다. Python↔Spring REST 계약은 D35로 소멸해 **내부 호출**이다.

> **쿼리 임베딩+RAG 검색 → 프롬프트 빌드 → 외부 foundation API(Opus) 추론 → 1차 인용검증.** 관련: [[component-specs]] §4 #11 · [[analysis-prompt-spec]] · [[v0.8-pending-law-corpus]] §4.6

## 역할
해소된 **시행예정 법령**에 대해 커맨드별 분석을 생성한다. RAG로 **시행중 법령** 근거를 끌어오고, Opus 4.8을 호출해 **구조화 `ImpactResult`** 를 반환한다. 자체/경량 학습 없음.

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `{ command, law, changedArticles, profile?, options{prompt_version,layer,max_tokens} }` | 대상은 시행예정 `Law`. 조문은 **변경분만**(실측 137→6) |
| 출력 | `{ result: ImpactResult, injected_source_ids[] }` | 2차 검증 게이트 입력 포함 |

## 파라미터 (설정)
| 파라미터 | 기본 | 설명 |
|---|---|---|
| `reasoning_model` | `claude-opus-4-8` | adaptive thinking, effort=high |
| `embedder` | 공유 [[Embedder]] | `mode="query"` |
| `retrieve_top_k` | 5~ | 시행중 법령 근거 검색 수 |
| `max_tokens` | 4000 | 구조화 JSON 출력 |
| `regen_max` | N | 인용검증 실패 시 재생성 횟수 |

## 동작
1. (필요 시) 쿼리 임베딩 [[Embedder]] `mode="query"` → Vector Index **분석용(`law` ns, 시행중 조문)** 검색
2. `source_id` 부여한 컨텍스트 조립([[analysis-prompt-spec]] §3 템플릿) — **변경 조문·`개정문`·부칙(시행일)**·기준선 조문·(Layer B면) 프로필
   ⚠️ 인용키는 **시행일 포함**: `LAW:{lawId}@{effectiveDate}:art:{no}` — 같은 법령ID에 시행예정본이 복수일 수 있다(D43)
3. **Opus API 호출**(constrained JSON, [[analysis-prompt-spec]] §4 스키마)
4. **1차 인용검증**(스키마 + 인용 존재성), 실패 시 재생성(≤`regen_max`)
5. 산출: `ImpactResult` + `injected_source_ids`(2차 게이트용); LawFacts(Layer A)·결과를 Law Store 캐시

## 2계층
- **Layer A**(프로필 무관): 법령 사실·조문 diff·`LawFacts` → **오프라인 선계산 후 캐시**. `ImpactSummary`/`LawDiff`.
- **Layer B**(프로필별): A를 입력으로 `PersonaImpact`/`ActionPlan`. 캐시 키는 `userId` 가 아니라 **프로필 속성 해시**(D41).

## 인터페이스 (Java, `com.lia.core.pipeline.analyze`)
```java
public class AnalysisEngine {                       // com.lia.core.pipeline.analyze
    AnalyzeResponse analyze(AnalyzeRequest req);
    List<SourceBlock> retrieve(Law law);            // query embed → law ns
    Prompt buildPrompt(AnalysisContext ctx);
    boolean verifyCitations(ImpactResult r, Set<String> injectedIds);
}
```

## 구조 결정 의도 (왜 이렇게)
- **쿼리 임베딩은 여기서**(검색용), 코퍼스 임베딩은 [[RAGIndexer]](적재용) — 같은 [[Embedder]]로 벡터공간 일치.
- **추론은 외부 API(Opus).** 임베딩·추론을 분리(별 벤더). 프롬프트/모델은 `prompt_version`·`meta`로 교체 가능.
- **2계층.** 사실층(A)을 프로필 무관으로 선계산·캐시 → 사용자 N명을 저렴하게(비용 레버, [[decision-log|D07]]). **변경 조문만 다루는 것도 같은 레버**다 — 실측 137→6조문.
- **인용 게이트 내장(1차)** + 오케스트레이터 2차(#12). `injected_source_ids`를 함께 반환해 2차 검증을 가능케 함([[component-specs]] §3.1).
- **명시적 워크플로.** 검색→조립→추론→검증→재생성(≤N)을 Spring 빈으로 직접 구현한다. 에이전트 프레임워크를 쓰지 않는 이유는 실행 경로가 설계 시점에 고정돼 있고 결정성·캐시·인용 감사가 1급 요건이기 때문이다([[decision-log|D37]]).

## 의존 / 관련
- 의존: [[Embedder]](query), Vector Index(`law` ns), Opus API, Law Store(캐시)
- 호출자: AnalysisPipeline(#8, **내부 호출**) · 규약: [[analysis-prompt-spec]]
