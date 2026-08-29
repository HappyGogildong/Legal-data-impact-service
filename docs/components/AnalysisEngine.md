---
title: AnalysisEngine — 컴포넌트 설계 (정합화)
status: Draft
date: 2026-08-27
tags: [component, pipeline, llm, grounding]
related: ["components/component-specs.md", "components/QueryPlanner.md", "components/LawStore.md", "prompts/analysis-prompt-spec.md", "adr/decision-log.md"]
---

# AnalysisEngine (Spring, 해석)

> 해소된 **시행예정 법령**에 대해, **LawStore에서 정확 조회한 정본으로 context를 조립** → 외부 foundation API(Opus 4.8) 추론 → 구조화 `ImpactResult` + 1차 인용검증. 관련: [[component-specs]] §4 #11 · [[analysis-prompt-spec]] · [[QueryPlanner]].
>
> **정합화 (2026-08-27).** 구 스펙의 "쿼리 임베딩 → `law` ns 시행중 조문 **RAG 검색**"은 **폐기**한다. 아래 근거로 **분석 경로에는 벡터 검색이 없다** — context는 조회로 확보한다.

## 왜 분석에 RAG 검색이 없나 (핵심 정합화)

- 대상이 **특정 시행예정 법령**(QueryPlanner가 이미 `LawRef`로 해소)이다. 그 **정본과 baseline(시행중본)은 `(lawId, effectiveDate)`로 [[LawStore]]에서 정확 조회**된다 — 후보를 "검색"할 필요가 없다.
- 분석에 필요한 재료(변경 조문·개정문·부칙·기준선 대응 조문)는 **그 두 애그리거트에 전부** 있다. **법령 전문은 임베딩하지 않고 context에 통째로** 넣는다(D55·CLAUDE.md).
- 벡터 검색([[ChunkStore]])은 **Discovery/LOOKUP**(어떤 법인지 찾기, `pending` ns)에서만 쓴다 — 그건 [[QueryPlanner]]/LawDiscovery의 몫이지 AnalysisEngine이 아니다.
- 따라서 [[Embedder]]·Vector Index는 **AnalysisEngine의 의존이 아니다**. (구 스펙의 `retrieve()`·`retrieve_top_k`·`embedder(mode=query)` 삭제.)

## 역할
해소된 시행예정 `Law`(+baseline)에 대해 차원별 분석을 생성한다. **context 조립 → Opus 호출 → 구조화 `ImpactResult` → 1차 인용검증**. 자체/경량 학습 없음. 검색 없음.

## 입력 / 출력
| | 타입 | 설명 |
|---|---|---|
| 입력 | `{ dimension(QueryType), law, baseline?, profile?, options{prompt_version, layer, max_tokens} }` | `law`=시행예정 정본, `baseline`=시행중본(제정이면 null). 조문은 **변경분만**(실측 137→6) |
| 출력 | `{ result: ImpactResult, injectedSourceIds[] }` | 2차 검증 게이트(#12) 입력 포함 |

## 동작 (명시적 워크플로)
1. **context 조립** — [[analysis-prompt-spec]] §3 템플릿으로 `source_id` 부여: **변경 조문 + baseline 대응 조문(DIFF) + 개정문 + 부칙(시행일) + (Layer B면) 프로필**. 벡터 검색 없음 — `law`/`baseline` 애그리거트에서 직접.
   ⚠️ 인용키는 **시행일 포함**: `LAW:{lawId}@{effectiveDate}:art:{no}`(복수 시행예정본, D43). 형식은 [[component-specs]] §1.3.
2. **Opus API 호출** — constrained JSON([[analysis-prompt-spec]] §4 스키마), adaptive thinking·effort=high.
3. **1차 인용검증** — 스키마 유효 + **인용 존재성**(모든 `claims[].citations` ⊆ 주입 `source_id`). 실패 시 재생성(≤`regen_max`).
4. 산출: `ImpactResult` + `injectedSourceIds`(2차 게이트용). Layer A 결과·`LawFacts`는 [[LawStore]] 캐시.

## 2계층
- **Layer A**(프로필 무관): 법령 사실·조문 diff·`LawFacts` → **오프라인 선계산 후 캐시**(`ImpactSummary`/`LawDiff`). 프로필 N명이 재사용(비용 레버, D07).
- **Layer B**(프로필별): A를 입력으로 `PersonaImpact`/`ActionPlan`. 캐시 키는 `userId`가 아니라 **프로필 속성 해시**(D41).

## 인터페이스 (Java, `com.lia.core.pipeline.analyze`)
```java
public class AnalysisEngine {
    AnalyzeResponse analyze(AnalyzeRequest req);         // context→Opus→검증
    AnalysisContext buildContext(AnalyzeRequest req);    // 정본에서 조립(+source_id). 검색 없음
    boolean verifyCitations(ImpactResult r, Set<String> injectedIds);  // 인용 존재성(1차)
}
```

## 구조 결정 의도 (왜 이렇게)
- **검색이 아니라 조회.** 대상이 특정된 뒤엔 정본을 정확 조회해 통째로 넣는 게 옳다 — 임베딩·top-k·재랭킹의 부정확성을 분석 경로에서 배제(그라운딩 무결성↑, 비용↓).
- **추론은 외부 API(Opus).** 프롬프트/모델은 `prompt_version`·`meta`로 교체 가능.
- **2계층.** 사실층(A)을 프로필 무관으로 선계산·캐시 → 사용자 N명을 저렴하게. **변경 조문만 다루는 것도 같은 레버**(137→6).
- **인용 게이트 내장(1차)** + 오케스트레이터 2차(#12). `injectedSourceIds`를 함께 반환해 2차 검증을 가능케 함.
- **명시적 워크플로**(조립→추론→검증→재생성≤N)를 Spring 빈으로 직접. 에이전트 프레임워크 미도입 — 실행 경로가 설계 시점에 고정이고 결정성·캐시·인용 감사가 1급(D37).

## 의존 / 관련
- 의존: **[[LawStore]]**(정본 조회·캐시), **Opus API**. ~~Embedder·Vector Index~~(분석 경로 무관 — 위 정합화).
- 호출자: `DimensionHandler`/`QueryDispatcher`(#10) — 내부 호출. 규약: [[analysis-prompt-spec]].
- 출력 소비: Verification Gate(#12, 2차).
