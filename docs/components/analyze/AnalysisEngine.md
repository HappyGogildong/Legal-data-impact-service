---
title: AnalysisEngine — 컴포넌트 설계 (정합화)
status: Draft
date: 2026-10-29
tags: [component, pipeline, llm, grounding]
related: ["components/component-specs.md", "components/plan/QueryPlanner.md", "components/store/LawStore.md", "prompts/analysis-prompt-spec.md", "adr/decision-log.md"]
---

# AnalysisEngine (Spring, 해석)

> 해소된 **시행예정 법령**에 대해, **LawStore에서 정확 조회한 정본으로 context를 조립** → 외부 foundation API(Opus 5.5, D68) 추론 → 구조화 `ImpactResult` + 1차 인용검증. 관련: [[component-specs]] §4 #11 · [[analysis-prompt-spec]] · [[QueryPlanner]].
>
> **정합화 (2026-08-27).** 구 스펙의 "쿼리 임베딩 → `law` ns 시행중 조문 **RAG 검색**"은 **폐기**한다. 아래 근거로 **분석 경로에는 벡터 검색이 없다** — context는 조회로 확보한다.
>
> **이번 증분 (Layer A · SUMMARY+DIFF).** 프로필 무관 두 차원만. `ContextBuilder`(조립)·`Reasoner`(Opus 심)·`FaithfulnessGate`(인용검증)를 얇은 `AnalysisEngine`이 조율. **범위 밖(후속):** Layer B(IMPACT·ACTION — `UserProfile` 미구현) · `LawFacts` 캐시 · Layer A **결과 캐시**(D51) · `QueryDispatcher` 배선 · 검증훅 #3(LLM-judge).

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
| 입력 | `AnalyzeRequest{ dimension(QueryType), law, baseline?, profile?, question?, situation }` | `law`=시행예정 정본, `baseline`=시행중본(제정이면 null). 조문은 **변경분만**(실측 137→6). `profile`·`question` 은 Layer B 만. `situation`(확인된 사실 `질문: 답` 목록, 기본 빈 목록)은 상황 재생성(`ACTION`)일 때만 채울 수 있다 — 다른 차원에 넣으면 생성자가 거부(D71). 프롬프트 버전·max_tokens 는 설정값(요청 필드 아님) |
| 출력 | `AnalyzeResponse{ result: ImpactResult, injectedSourceIds[] }` | 끝까지 인용 검증 실패면 `InsufficientGroundingException`(디스패처가 차원별 unmet 으로) |

## 동작 (명시적 워크플로)
1. **context 조립** — [[analysis-prompt-spec]] §3 템플릿으로 `source_id` 부여: **변경 조문 + baseline 대응 조문(DIFF) + 개정문 + 부칙(시행일) + (Layer B면) 프로필**. 벡터 검색 없음 — `law`/`baseline` 애그리거트에서 직접.
   ⚠️ 인용키는 **시행일 포함**: `LAW:{lawId}@{effectiveDate}:art:{no}`(복수 시행예정본, D43). 형식은 [[component-specs]] §1.3.
2. **Opus API 호출** — constrained JSON([[analysis-prompt-spec]] §4 스키마), adaptive thinking(항상 켜짐)·effort=medium(D68).
3. **1차 인용검증** — 스키마 유효 + **인용 존재성**(모든 `claims[].citations` ⊆ 주입 `source_id`). 실패 시 재생성(≤`regen_max`).
4. 산출: `ImpactResult` + `injectedSourceIds`. 쉬운 요약(SUMMARY)은 배치가 이 엔진으로 미리 만들어 `law_summaries` 에 저장하고(D67, [[IngestJob]] SUMMARIZE), 읽을 때 `GroundedSummaries` 가 같은 `grounded` 규칙으로 지금의 근거 집합에 다시 대 본다. **(후속)** `LawFacts`·답변 캐시(D51).

- **Layer B 입력·검증(D69):** `AnalyzeRequest` 에 `profile`·`question`(Layer B 일 때만 non-null). `ContextBuilder` 는 Layer B 에도 현행 기준선 블록을 넣는다. 검증은 Layer A 규칙 + 영향·대응 둘 다 ≥ 1·항목마다 인용. 프로필은 근거 집합 밖이라 인용하면 실패.
- **Layer B 상황 질문·단계 설명(D71):** 첫 Layer B 는 `Reasoner.reasonPersonalized` 로 **Layer B 전용 출력 레코드**(`PersonalizedReply` — `ImpactResult` 필드 + `actions[].situationNote` + `situationQuestions[]`)를 받아 `PersonalizedOutput`(`ImpactResult` 그대로 + 질문 + 단계 설명)으로 나눈다. `ImpactResult`·SYSTEM·`PROMPT_VERSION` 은 바뀌지 않는다([[analysis-prompt-spec]] §5.1). 기존 D69 그라운딩은 그대로 하고, 거기에 두 필터를 더한다 — **질문 필터**: `basis` 가 주입 집합 밖이거나 형식이 깨진 질문(문장 비었음·200자 초과 또는 줄바꿈 포함·`CHOICE` 선택지가 2~4개가 아님·선택지 40자 초과)은 **그 질문만** 버리고(답 전체 실패 아님), 최대 3개만 남기고 `q1`·`q2`·`q3` 를 다시 붙인다. **단계 설명 필터**: 범위 밖 단계 번호·빈 문장·200자 초과 또는 줄바꿈 포함 설명은 버리고, 같은 단계는 처음 것만 남긴다.
- **ACTION 재생성 그라운딩(D71):** `AnalyzeRequest.dimension == ACTION`(상황 확인 후 대응 방안만)이면 `ContextBuilder` 가 같은 근거 블록 + `situation` 을 `[SITUATION]` 으로 붙이고, 검증은 actions ≥ 1 · 각 `basis` ≥ 1 · 모든 인용 ⊆ 주입 집합(claims·impacts 필수 아님). 상황 사실은 근거 집합 밖이라 인용하면 실패 → 재생성 ≤2 → `InsufficientGroundingException`. 재생성은 새 질문을 내지 않는다(질문은 첫 응답에서 세션에 보관된 것을 쓴다). 한도 차감은 엔진이 아니라 호출자([[ActionRevisionUseCase]])가 호출 직전에 1회 한다.

## 2계층
- **Layer A**(프로필 무관, SUMMARY·DIFF): 지금은 SUMMARY 만 오프라인 선계산(D67) — 모든 사용자가 재사용(비용 레버, D07). DIFF 는 요청 시 생성. `LawFacts` 는 후속.
- **Layer B**(프로필별, IMPACT·ACTION): 정본 근거 + 프로필 속성 + 질문으로 **Opus 1회**에 둘 다 생성(D69). 저장하지 않는다 — 답변 캐시를 도입하면 키는 `userId`가 아니라 **프로필 속성 해시**(D41).

## 구조 (컴포넌트)

**얇은 오케스트레이터 + 3 협력자** — 순수 로직과 LLM 경계를 갈라 각각 독립 검증한다. 도메인 `com.lia.core.domain.analysis`, 파이프라인 `com.lia.core.pipeline.analyze`.

| 클래스 | 역할 |
|---|---|
| `AnalysisEngine` | 오케스트레이터(얇음) — `조립 → 추론 → 검증 → 재생성(≤N) → 폴백`. 흐름·정책만. |
| `ContextBuilder` | **결정론** — 해소된 `Law`+`baseline`에서 차원별 근거 블록 조립 + **`source_id` 부여**. LLM 없음, 단위 테스트 가능. 그라운딩 임계(주입 `source_id` = 게이트 기준). |
| `Reasoner` (포트) | Opus 호출 경계. `SpringAiReasoner`(ChatClient `.entity(ImpactResult)`, §3 템플릿+차원 변형). 응답의 thinking 블록은 `ThinkingBlockFilter` advisor 가 걷어 낸다(D68 ⑤). **포트인 이유 = 재생성 루프 테스트 심**(스크립트된 결과 주입), 교체용 아님. |
| `FaithfulnessGate` | 인용 존재성 검증(§6.2) — **기존 클래스 재사용**(D08 그라운딩 규칙, `eval`↔런타임 공용). `passes(claims, injectedIds)`. CitationGate를 따로 만들지 않는다(규칙 이중화 금지). |

- **PromptBuilder를 따로 두지 않는다** — 프롬프트 조립은 `Reasoner` 내부(과분할 회피). 분리 기준 = "레이어를 위한 레이어"가 아니라 **순수/LLM 경계 + 테스트 가능성**.
- `FaithfulnessGate`는 지금 `com.lia.core.eval`에 있으나 실은 **도메인 그라운딩 규칙**이라 `domain/analysis`로 이전 검토(런타임이 eval을 의존하는 어색함 해소). eval의 `faithfulness` 지표가 같은 규칙을 감싼다.

## 도메인 타입 (`domain/analysis`)
- `ImpactResult`(§4) + `Claim`·`Impact`·`Action`·`EffectiveInfo`·`Meta`. **LLM 출력이라 record는 관대**(필드 생략 허용), **불변식(인용 존재성)은 `FaithfulnessGate`가 강제**(record 아님). `affected_profiles`는 제거(D57).
- `AnalyzeRequest{dimension, law, baseline?, profile?, question?, situation}` — **우리 판정 타입이라 생성자가 `dimension·law` 비-null 과 `profile ≠ null ⇔ Layer B` 를 강제**(ContextBuilder는 assembly layer라 방어 안 함). Layer A 용 3인자 생성자가 있다. `AnalyzeResponse{result, injectedSourceIds, situation}` — `situation`(`Situation{questions, stepNotes}`)은 첫 Layer B 에서만 채워지고 그 외엔 `Situation.none()`(D71). 상황 타입(`SituationQuestion`·`SituationAnswer`·`ConfirmedFact`·`StepNote`·`Situation`)은 `domain/analysis`.
- `AnalysisContext`·`SourceBlock`은 **불변**(`List.copyOf`). `SourceBlock.type`은 문자열이 아니라 **`SourceType` enum**(ARTICLE·AMEND·ADDENDA·BASELINE — 오타 컴파일 차단, 프롬프트/필터 안전). *baseline은 엄밀히 role이나 MVP에선 type로 둠(role 분리 후속).*

## source_id 생성 권위 (리뷰 반영)
- **모든 `source_id` 포맷은 `Law` 애그리거트가 만든다** — `sourceId(Article)`(art) · `amendSourceId()` · `addendumSourceId(Addendum)` · `ref()`(요약). `ContextBuilder`·`RAGIndexer`·검색은 **포맷을 몰라도 되게** 한다(문자열 조합 금지). source_id는 lawId@efYd를 인코딩하므로 그걸 아는 `Law`가 유일한 권위(§1.3).
- **인용 대상만 source_id를 갖는다** — 조문·부칙·개정문·요약(근거). 메타(제목·소관부처·날짜·revision)는 증거가 아니라 식별키라 없음.
- **baseline source_id = `LAW:{lawId}:art:{no}`(시행일 없음) 유지**(§1.3) — 시행중본은 시점상 1개라 모호하지 않고, 법 변경 시 pending `revision`이 바뀌어 캐시 무효화(D51)라 revision 내 역추적 안정.
- **DIFF + baseline=null = 제정**(현행본 없음, D42)의 정상 상태 → fail-fast 아님(전부 신설).

> **`source_id` 부여 ≠ Layer A 캐시.** `source_id`는 `ContextBuilder`가 조립 시 각 근거 블록에 붙이는 **인용·그라운딩 키**(`LAW:{lawId}@{efYd}:art:{no}`)다. Layer A **캐시**는 프로필 무관 결과(ImpactResult/LawFacts)를 `revision`으로 재사용하는 별개 층(D07/D51, 이번 범위 밖) — 그 캐시된 결과 *안의 인용*이 곧 source_id다. 지금은 요청 시 온디맨드 산출.

## 구조 결정 의도 (왜 이렇게)
- **검색이 아니라 조회.** 대상이 특정된 뒤엔 정본을 정확 조회해 통째로 넣는 게 옳다 — 임베딩·top-k·재랭킹의 부정확성을 분석 경로에서 배제(그라운딩 무결성↑, 비용↓).
- **추론은 외부 API(Opus).** 프롬프트/모델은 `prompt_version`·`meta`로 교체 가능.
- **2계층.** 사실층(A)을 프로필 무관으로 선계산·캐시 → 사용자 N명을 저렴하게. **변경 조문만 다루는 것도 같은 레버**(137→6).
- **인용 게이트 내장(1차)** + 오케스트레이터 2차(#12). `injectedSourceIds`를 함께 반환해 2차 검증을 가능케 함.
- **명시적 워크플로**(조립→추론→검증→재생성≤N)를 Spring 빈으로 직접. 에이전트 프레임워크 미도입 — 실행 경로가 설계 시점에 고정이고 결정성·캐시·인용 감사가 1급(D37).

## 계측
- Opus 호출은 Spring AI **`gen_ai.*`** 내장 계측(토큰·모델·지연). 별도 `lia.*` 스팬 없음(임베딩 교훈 동일). 재생성 횟수는 필요 시 카운터로.

## 검증
- **상황 확인(D71):** 질문 필터(근거 밖·형식 깨짐 제거, 최대 3, id 재부여) · 단계 설명 필터 · ACTION 재생성 그라운딩(actions ≥ 1·basis ≥ 1·상황 사실 인용 시 실패) · Layer B 출력 분할(`PersonalizedReply` → `ImpactResult` + 질문 + 단계 설명) · Layer A 메시지에 `[SITUATION]` 없음.
- **단위(무비용):** `ContextBuilder`(차원별 블록·`source_id` 형식·baseline 대응) · `FaithfulnessGate`(유효/환각 인용 — 기존 테스트) · `AnalysisEngine` **오케스트레이션**(`FakeReasoner`로 "첫 응답 환각→재생성→정상" 재생성 루프, N회 초과 시 폴백, `injectedSourceIds` 반환).
- **게이트 라이브 스모크(수동):** `SpringAiReasoner` 실 Opus — `LIA_ANALYZE_LIVE=1` 옵트인(비용). SUMMARY/DIFF 실 생성이 스키마·인용 존재성을 만족하는지 실측.

## 의존 / 관련
- 의존: **[[LawStore]]**(정본 조회 — 이번 증분 캐시 아님), **Opus API**(Anthropic ChatClient), `FaithfulnessGate`. ~~Embedder·Vector Index~~(분석 경로 무관 — 위 정합화).
- 호출자: `DimensionHandler`/`QueryDispatcher`(#10, 후속) — 내부 호출. 규약: [[analysis-prompt-spec]].
- 출력 소비: Verification Gate(#12, 2차).
