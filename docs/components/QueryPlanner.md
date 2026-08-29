---
title: Query Planner — 컴포넌트 설계 · 구현 착수
status: Draft
version: 0.2
date: 2026-08-27
tags: [component, pipeline, planner, nl]
related: ["components/component-specs.md", "components/SourceAnalyzer.md", "mvp/service-api-spec.md", "architecture/v0.9-nl-query-planner.md"]
---

# Query Planner (Spring, 질의 계획)

> 자연어 질의를 **엄격한 타입 DTO(`AnalysisQuery`)** 로 번역하고, 타입에 따라 해소·검색·실행을 라우팅한다. LLM=번역기 → 타입 DTO → 결정론 dispatch[^orm]. 관련: [[v0.9-nl-query-planner]] §2 · [[service-api-spec]] · [[component-specs]] §1
>
> **이번 증분 (2026-08-27 확정) — 계획까지(`PlanResult`).** `QueryTranslator` + `QueryPlanner`만. Reference는 [[SourceAnalyzer]]에 연결, Discovery는 criteria 구성까지(검색 실행 없음). **`QueryDispatcher`·`DimensionHandler` 실제 실행은 [[AnalysisEngine]] 랜딩 시 후속.** 패키지 `com.lia.core.pipeline.plan`(ResolutionResult가 `pipeline/resolve`에 사는 것과 동형). 비법령·오프토픽(S9)은 **번역기의 `isLawQuery` 신호**로 Planner가 조기 거부(UNVERIFIED).

## 역할

`POST /analyses`로 들어온 자연어를 받아 **"이 질문이 무엇을 요구하는가"를 구조화**한다. LLM의 자유도를 *번역 한 단계*로 좁혀, 이후 실행 경로는 결정론으로 고정한다 — 이것이 D37(에이전트 미도입)을 강화하는 핵심 장치다. 자유 텍스트는 번역기 입구에서 끝난다.

## 1. 자연어 질의 시나리오 → 파이프라인

QueryType 5종: **`LOOKUP`**(발견) · `SUMMARY` · `DIFF` · `IMPACT` · `ACTION`.
Target 2형: **`Reference`**(해소된 1건) · **`Discovery`**(코퍼스 검색 → 후보 N건).

| # | 자연어 질의 | 타입(주/집합) | Target | 파이프라인 | 응답 |
|---|---|---|---|---|---|
| S1 | "주택법 바뀌면 나 전세 계약 어떻게 돼?" | IMPACT / {DIFF,IMPACT,ACTION} | Reference | 해소 → diff(캐시)+영향+대응 | 여러 차원 |
| S2 | "주택법 제18조 뭐가 바뀌었어?" | DIFF / {DIFF} | Reference(art:18) | 해소 → 해당 조문 diff | 조문 대비 |
| S3 | "주택법이 대체 무슨 법이야?" | SUMMARY / {SUMMARY} | Reference | 해소 → 선계산 요약 | 요약 카드 |
| S4 | "나한테 영향 있을 시행예정 법령 찾아줘" | LOOKUP / {LOOKUP} | Discovery(프로필) | 코퍼스 검색 | 후보 랭킹 |
| S5 | "나한테 영향 있을 법령 찾아서 분석해줘" | LOOKUP / {LOOKUP,IMPACT,ACTION} | Discovery(프로필) | 검색 → top-K 분석(팬아웃) | 후보+차원 |
| S6 | "요즘 주거 관련 뭐 바뀌는 거 없어?" | LOOKUP / {LOOKUP} | Discovery(도메인=주거) | 검색(도메인/키워드) | 후보 |
| S7 | "전세 세입자한테 불리해지는 법 있어?" | LOOKUP / {LOOKUP,IMPACT} | Discovery(조건=전세) | 검색 → 영향 판정 | 후보+영향 |
| S8 | "가상의무슨법 바뀌면 어떻게 돼?" | IMPACT / {…} | Reference(해소 실패) | 해소 → NOT_FOUND_YET | 4상태 안내 |
| S9 | "오늘 점심 뭐 먹지" | (저신뢰) | — | UNVERIFIED/비법령 | "확인 안 됨" |

**Reference (S1~3,S8):** 번역이 법령 지목(`lawName`/`articleNo`) → `SourceAnalyzer.resolve`. `RESOLVED`→분석 dispatch, `AMBIGUOUS`(시행예정본 복수 D43)→시행일 선택, `NOT_FOUND_YET`/`UNVERIFIED`→**fail-closed(분석 안 함)**.

**Discovery/LOOKUP (S4~7):** 특정 법령 없음 → `LawDiscovery`가 `pending` ns + 프로필/도메인/조건으로 코퍼스 검색 → 실재 후보 랭킹. 분석 타입 동반 시 top-K 팬아웃. 프로필 기반인데 프로필 없으면 키워드/도메인으로 강등하거나 프로필 유도. 검색은 실재 법령만 반환(fail-closed).

**자유도 보존:** 번역기는 타입 **집합**을 느슨하게 추출한다(주 1 + 집합). 안 떨어지는 질의는 best-effort(기본 SUMMARY/LOOKUP), dispatcher가 온 집합을 처리 — 분류 실패로 거부하지 않는다.

## 2. 입력 / 출력

| | 타입 | 설명 |
|---|---|---|
| 입력 | `{ query: String, lawRef?: LawRef, scope?: QueryType[] }` | 자연어 + (선택) 이미 특정된 법령·차원 |
| 출력 | `PlanResult` = `Planned(AnalysisQuery)` \| `Unresolved(ResolutionResult)` | 검증된 질의 또는 4상태 거부 |

```
AnalysisQuery {
  primaryType: QueryType             // FE 주 뷰·주 검색
  types: Set<QueryType>              // 채울 차원(포괄질문=복수)
  target: Target                     // sealed: Reference(LawRef) | Discovery(DiscoveryCriteria)
  entities: { lawName?, articleNo?, keywords[], conditions[], domains[] }
  intentSummary: String              // 알고 싶은 것 한 줄
  filters: { articleScope: CHANGED_ONLY|ALL }
  profileBound: boolean              // Layer B 채울 수 있는지
  options: { language, promptVersion }
}
```

## 3. 컴포넌트

### 이번 증분 (계획까지)

- **`QueryTranslator`** (포트) — `AnalysisQueryDraft translate(String query)`. **유일한 LLM 자유도**를 인터페이스로 격리한다. *포트를 두는 이유는 "교체 예정"이 아니라 **테스트 심***: 플래너의 결정론 로직을 LLM 없이(비용 0·결정론) 검증하려면 번역 결과를 가짜로 주입해야 한다(`ChatClient` 목은 플루언트 체인이라 지저분하고 행위 대신 구현을 테스트).
- **`SpringAiQueryTranslator`** (구현) — `ChatClient.prompt().user(nl).call().entity(AnalysisQueryDraft.class)`. **모델 Haiku 4.5**(추출·분류는 저비용; Opus는 실제 분석 생성용 — 티어링, [[component-specs]] §3.3).
- **`AnalysisQueryDraft`** (번역기 LLM 출력) — `primaryType·types·`**`isLawQuery`**`(법령의도 신호)·targetKind(REFERENCE|DISCOVERY)·lawName?·articleNo?·keywords·conditions·domains·intentSummary`. 플래너가 해소 후 `AnalysisQuery`로 매핑.
- **`QueryPlanner`** — `plan(query, explicitLawRef?, profilePresent) → PlanResult`:
  1. `translate` → draft.
  2. **`!draft.isLawQuery` → `Unresolved(UNVERIFIED)`** — 오프토픽(S9) 조기 거부(fail-closed).
  3. REFERENCE(또는 explicitLawRef): `SourceAnalyzer.resolve` → `RESOLVED`→`Planned`(Reference) / `AMBIGUOUS`·`NOT_FOUND_YET`·`UNVERIFIED`→`Unresolved`.
  4. DISCOVERY: `DiscoveryCriteria` 구성 → `Planned`(검색 실행은 dispatch라 이번 범위 밖).
  5. **프로필 게이팅**: `profilePresent` 아니면 Layer B(`IMPACT`·`ACTION`) 제거·`profileBound=false`. 타입이 비면 기본 `SUMMARY`(Reference)/`LOOKUP`(Discovery) — **분류 실패로 거부하지 않는다**(best-effort, 자유도 보존).

### 후속 (AnalysisEngine 랜딩 시)

- **`QueryDispatcher`** — `AnalysisQuery`를 QueryType별 핸들러로 라우팅·조립. `types`+`Target` 결정론. Discovery+분석은 top-K 팬아웃.
- **`DimensionHandler`** (interface) + 구현 — 실제 RAG/LLM/검색 실행. Embedder·ChunkStore·AnalysisEngine·LawDiscovery 의존.

## 3.1 타입 (`pipeline/plan`)

- `QueryType` enum(5종) · `Target` sealed: `Reference(LawRef)` | `Discovery(DiscoveryCriteria)` · `LawRef{lawId, effectiveDate?, articleNo?}`.
- `AnalysisQuery{primaryType, types:Set, target, entities, intentSummary, filters(articleScope), profileBound, options}` — **생성자가 불변식 강제**(fail-closed 승계: primaryType∈types 등).
- `PlanResult` sealed: `Planned(AnalysisQuery)` | `Unresolved(ResolutionResult)`.

## 3.2 계측 · 검증

- **계측**: 별도 `lia.plan` 스팬 없음 — Haiku 호출은 Spring AI `gen_ai.*`, 해소는 기존 `lia.resolve`가 커버(이중 스팬 회피, 임베딩 교훈 동일).
- **단위**: `FakeQueryTranslator`(캔드 draft) + `SourceAnalyzer`(기존 `FakeLookup` 패턴) → Reference RESOLVED→Planned / 비법령→Unresolved(UNVERIFIED) / 무프로필→Layer B 제거 / Discovery→Planned criteria / AMBIGUOUS→Unresolved.
- **라이브 스모크(수동·게이트)**: `SpringAiQueryTranslator` 실 Haiku 호출 — `LIA_PLAN_LIVE`+`ANTHROPIC_API_KEY` 옵트인(비용, 사용자 실행). 기본 test에서 스킵.

## 4. 구조 결정 의도 (왜 이렇게)

- **LLM = 번역기, 실행 = 결정론(D46).** 자연어 입력 ≠ 동적 제어. 번역기가 타입 DTO를 뱉는 순간 이후 경로는 고정되어, 결정성·캐시·인용 감사가 보존된다(D37 강화). 에이전트를 넣으면 같은 질문이 매번 다른 경로 → 캐시 미스·인용 사슬 위험·지연 — 법률 그라운딩 서비스엔 부채.
- **타입이 검색 전략을 고른다(비용 레버).** LOOKUP·SUMMARY·DIFF는 캐시/검색이라 RAG·LLM 불필요. 온라인 LLM 비용은 IMPACT·ACTION에만. "Lookup에 비싼 RAG 안 돌린다"가 우리 선계산 구조에선 더 강하다.
- **DTO가 로깅·캐시·디버깅 지점.** "AI가 왜 이상한 법령을 참조?"를 *번역 단계(DTO)* 오분류인지 *실행 단계* 실패인지 분리 진단. 캐시 키 = 정규화된 `AnalysisQuery`.
- **Reference vs Discovery 분리.** "주택법 바뀌면?"(해소된 1건)과 "나한테 영향 있을 법 찾아줘"(코퍼스 검색 N건)는 근본적으로 다른 연산. target을 sealed로 나눠 dispatcher가 형태별로 처리.
- **fail-closed 승계.** Reference 미해소·Discovery 무결과 모두 지어내지 않는다. 불변식은 `ResolutionResult`·`AnalysisQuery` 생성자가 강제.
- **번역기는 저비용 모델.** 추출·분류에 Opus는 과하다. Haiku로 질의당 비용을 낮춘다.

## 5. 의존 / 관련

- 의존: `ChatClient`(Anthropic Haiku), `SourceAnalyzer`(Reference 해소), `LawDiscovery`(Discovery 검색, 후속), `LawLookup`.
- 소비: `QueryDispatcher` → 차원 핸들러(AnalysisEngine 본체).
- D37 격리: 멀티턴·위임추적(동적 깊이) 도입 시 이 컴포넌트가 에이전트 삽입점.

---

[^orm]: **설계 유비(참고).** 명칭은 **Query Planner**로 통일한다. 아래는 이해를 돕는 비유일 뿐 명명·구현 근거가 아니다 — ORM이 표현식을 SQL로 컴파일하듯, 여기서는 LLM이 자연어를 타입 DTO로 컴파일하고 이후는 결정론으로 실행한다. `AnalysisQuery`↔Query 표현식, `QueryTranslator`(여기만 LLM)↔Mapper, `QueryDispatcher`↔Query Builder, 차원 핸들러↔Executor/Session.
