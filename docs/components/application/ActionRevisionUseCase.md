---
title: ActionRevisionUseCase — 대응 방안 상황 확인 재생성 유스케이스 (application 계층)
status: Draft
version: 0.1
date: 2026-10-29
tags: [component, application, usecase, personalization, situation]
related: ["components/component-specs.md", "components/application/AnalyzeUseCase.md", "components/dispatch/QueryDispatcher.md", "components/analyze/AnalysisEngine.md", "components/web/AnalysisApi.md", "backend/cost-policy.md"]
---

# ActionRevisionUseCase (application 계층, 온라인 유스케이스)

> 첫 Layer B 응답이 낸 **상황 질문**에 사용자가 답하면, 그 답을 근거 조문과 함께 모델에 주어 **대응 방안만 다시 생성**하는 유스케이스(D71). 분석 전체를 다시 하지 않는다 — 영향 등 다른 차원은 그대로. 관련: [[AnalyzeUseCase]](첫 분석) · [[AnalysisApi]](web 어댑터) · [[component-specs]] §4 #8.

## 계층·경계

```
web (HTTP 어댑터)                   →  application (유스케이스)   →  pipeline(블록) · domain · store
ActionRevisionController               ActionRevisionUseCase        PersonalAdmission · AnalysisEngine
HttpSessionSituationQuestions(포트 구현)
```

- **담당**: 재생성 게이트 순서(개인화 → 만료 → 답 검증 → 정본·기준선 → 한도) 조합, 엔진 ACTION 1회 호출, 결과를 `Revised`/`NotRevised` 타입으로 표현.
- **담당 안 함**: HTTP·세션 저장소(web — 세션은 `SituationQuestions` 포트 뒤에 있어 이 계층은 세션을 모른다) · 프로필 규칙([[ProfileUseCase|PersonalizationGate]]) · 기준선·한도 판정 함수([[QueryDispatcher]] 의 `PersonalAdmission` — 첫 Layer B 와 공유) · 프롬프트·LLM·그라운딩([[AnalysisEngine]]).

## 구조 (컴포넌트)

`com.lia.core.application.analysis` (포트·유스케이스), 어댑터는 `com.lia.core.web`.

| 클래스 | 역할 |
|---|---|
| `ActionRevisionUseCase` | 유스케이스 — `revise(userId, ref, answers, questions)`. 게이트 순서대로 확인한 뒤 엔진을 ACTION 으로 1회 호출. Spring 애노테이션 없음 — 배선은 `PipelineConfig` 의 `@Bean`([[AnalyzeUseCase]] 와 같은 이유). |
| `RevisionOutcome` (sealed) | 결과 — `Revised`(대응 방안 `ImpactResult` + 단계 설명 `stepNotes` + 근거 집합) \| `NotRevised`(`Unmet` 사유). HTTP 타입 비의존. |
| `SituationQuestions` (포트) | 질문 보관소 — `save(LawRef, List<SituationQuestion>)` · `find(LawRef) → Optional<List<SituationQuestion>>`. 이 계층은 포트만 안다. |
| `HttpSessionSituationQuestions` (web 어댑터) | 포트 구현 — HTTP 세션 속성 하나에 법령 버전(`lawId@yyyy-MM-dd`) → 질문 목록. **최근 20개 LRU**, 같은 법령은 덮어쓴다. 세션에는 **질문만**(답 없음), DB 저장 없음. |

협력자(생성자 주입): `PersonalizationGate`(D69) · `LawSource`(정본·기준선) · `PersonalizationQuota`(하루 한도) · `AnalysisEngine`. 기준선·한도·정본 없음 판정은 `PersonalAdmission` 공통 함수를 쓴다(디스패처의 첫 Layer B 경로와 같은 규칙 — 중복하지 않는다).

## Contract

`revise(Optional<UUID> userId, LawRef ref, List<SituationAnswer> answers, SituationQuestions questions) → RevisionOutcome`

- **전제**: 로그인 세션(web 가 인증 필수·CSRF 로 보장). `answers` 는 `questionId`+`answer` 만 — 질문 문장은 클라이언트가 보내지 않는다(서버가 세션의 질문을 쓴다, 위조 차단).
- **게이트 순서** (앞에서 걸리면 뒤는 실행하지 않는다):
  1. 개인화(`PersonalizationGate`) — Blocked 면 그 사유(`PROFILE_REQUIRED`·`CONSENT_OUTDATED`) `NotRevised`.
  2. 세션 질문 조회 — 그 법령 버전의 질문이 없으면 `SITUATION_EXPIRED`.
  3. 답 검증 — 모르는 `questionId`·중복·형식 위반·빈 목록·전부 `모름` → `IllegalArgumentException`(web 가 400). 정상 사용으론 일어나지 않으므로 위조로 본다.
  4. 정본 없음 → `LAW_NOT_LOADED` · 개정본인데 기준선 없음 → `BASELINE_NOT_LOADED`.
  5. 하루 한도 `tryConsume` 실패 → `DAILY_LIMIT`.
  6. 엔진(ACTION) 1회 — 근거 부족이면 `INSUFFICIENT_GROUNDING`.
- **보장**: `Revised` 는 인용 검증을 통과한 대응 방안뿐이다(actions ≥ 1 · 각 `basis` ≥ 1 · 모든 인용 ⊆ 주입 집합). 확인된 사실(`질문: 답`, `모름` 제외)은 모델에게 근거가 아닌 상황 정보로만 간다 — 인용하면 실패.
- **재생성 한도**: 한 번의 `revise` 는 하루 한도 **1회**를 쓴다(엔진 안 재생성 ≤2 는 한도 추가 차감 없음 — D69 와 같은 규칙). 첫 Layer B 와 같은 한도를 공유한다([[cost-policy]]).

## Invariants

- **세션엔 질문만, 답은 없다.** 답은 요청 처리 중에만 쓰고 버린다. 프롬프트엔 `질문: 답` 줄과 프로필 라벨만 가고 userId 는 없다. 답 내용은 로그·트레이스에 남기지 않는다. (D69 "개인화 답 저장 안 함" = DB 영구 보존 안 함 — 메모리 세션·탭 저장소는 허용, D71.)
- **한도 1회·환불 없음.** 호출 직전에 차감하고, 실패해도 돌려주지 않는다(D69).
- **fail-closed.** 질문이 없거나 만료됐거나 답이 위조·형식 위반이면 모델을 부르지 않는다. 근거 부족이면 기존 대응 방안을 그대로 두고(웹) 새 결과를 만들지 않는다.
- **원문 질문 미사용.** 재생성 프롬프트에는 사용자의 자유 질문 원문을 넣지 않는다.

## Side Effects

- 개인화 하루 한도 **1회 차감**(`PersonalizationQuota`, 호출 직전).
- Opus 호출 **1회**(대응 방안만, 인용 검증 실패 시 재생성 ≤2).
- 정본·기준선 읽기. 그 외 쓰기·캐시·이벤트 없음. 세션 저장은 첫 분석 쪽(`AnalysisController`)이 하며 이 유스케이스는 읽기만 한다.

## 검증

- `ActionRevisionUseCaseTest`(in-JVM, 가짜 엔진·가짜 한도·가짜 `SituationQuestions`) — **게이트 순서**(개인화 → 만료 → 답 검증 → 기준선 → 한도 → 엔진; 앞 게이트에서 걸리면 뒤 협력자 무접촉) · 한도 정확히 1회 차감 · 엔진 ACTION 1회 · `모름` 제외된 사실만 전달 · 근거 부족 → `INSUFFICIENT_GROUNDING`.
- `HttpSessionSituationQuestionsTest` — 법령별 덮어쓰기 · 최근 20개 LRU · 세션에 답이 없음.
- API 슬라이스(`ActionRevisionControllerTest`) — 200 · unmet 사유별 · 400 · CSRF 없으면 403 · OpenAPI 스냅샷 ([[AnalysisApi]]).

## 의존 / 관련

[[QueryDispatcher]](`PersonalAdmission`) · [[AnalysisEngine]](ACTION 그라운딩) · [[ProfileUseCase]](`PersonalizationGate`) · [[AnalysisApi]](web 어댑터·`POST /analyses/action-revisions`) · [[AnalyzeUseCase]](첫 분석) · [[cost-policy]](한도 공유) · [[decision-log]] D71 · [[component-specs]] §4 #8.
