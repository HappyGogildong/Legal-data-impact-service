---
title: LLM 분석 엔진 — API 호출 프롬프트 정의서
status: Draft
version: 0.2
date: 2026-08-14
tags: [prompt, llm, analysis-engine, spec]
related:
  - "architecture/v0.9-nl-query-planner.md"
  - "components/component-specs.md"
  - "components/QueryPlanner.md"
  - "backend/observability.md"
---

# LLM 분석 엔진 — API 호출 프롬프트 정의서 (v0.2 Draft)

**관련:** [[v0.9-nl-query-planner|아키텍처 v0.9]] §4.6 · [[component-specs]] §1·§3 · [[QueryPlanner]]

> **v0.1 → v0.2 (D41·D42·D44·D46·D51, 2026-08-14):** 대상이 *의안(Bill)* → **시행예정 법령(Law)**, 개인화 입력이 *Nemotron 세그먼트* → **자기신고 프로필**, 인용키 `BILL:` → **`LAW:{lawId}@{efYd}`**, 커맨드(사용자 선택) → **차원(플래너가 고르는 가드레일)**, 캐시는 **prompt caching + 완전동일 답만**(D51). 이 문서는 **Opus 생성 콜**의 프롬프트를 규정한다(질의 번역은 Haiku, [[QueryPlanner]]).

## 0. 목적·전제

외부 **foundation 모델 API**(Claude Opus 4.8)를 호출해 입법 영향 분석을 생성할 때 **프롬프트를 어떻게 구성하고 응답을 어떤 형식으로 받을지** 규정한다.

전제:
- 모델은 **외부 API**. 자체 학습 없음(D19·D32). 번역·분류는 Haiku, 생성은 Opus(티어링).
- **RAG/Store = 컨텍스트 공급원.** 모델이 모르는 시행 예정 법령을 호출 시점에 주입한다.
- **모든 주장은 조문 `source_id`로 역추적**(그라운딩·인용검증). 파라미터 기억 의존 금지.
- 출력은 **검증·렌더 가능한 구조화 JSON**.
- **답은 사용자의 실제 질문에 맞춘다**(D51) — 차원은 답변 구조·가드레일이지 사용자가 고르는 버튼이 아니다.

---

## 1. 프롬프트 입력 계약 (필요 조건) ★

호출 1건을 만들기 위해 **무엇이 갖춰져야 하는가**. `QueryDispatcher`(#8)가 조립한다.

| # | 입력 요소 | 출처 | 필수성 | 설명 |
|---|---|---|---|---|
| 1 | **시스템 역할·정책** | 시스템(상수) | 필수 | "시행 예정 법령 영향 분석가. 근거 없는 주장 금지. 법률자문 아님." 불변 가드레일 |
| 2 | **태스크 지시** | 시스템(차원별) | 필수 | 이 호출이 채울 차원(SUMMARY/DIFF/IMPACT/ACTION) |
| 3 | **법령 코어 메타** | Law Store | 필수 | `lawId, title, status, effectiveDate, amendKind, ministry` |
| 4 | **변경 조문 본문** | Law Store | 필수 | `articles[changed=true]{no, text, changeType}` + 부칙(시행일·경과조치) + 개정문. **본문 확보됨**(D42, 획득 갭 해소) |
| 5 | **현행 기준선 + diff** | Law Store/RAG | DIFF·IMPACT 필수 | 같은 `lawId` 시행중본 조문 + `diffVsCurrent` |
| 6 | **LawFacts** | Layer A 파생 | 조건부 | 의무·권리·벌칙·기한·`thresholds`(적용 기준) |
| 7 | **자기신고 프로필** | User Profile Store | **IMPACT·ACTION 필수** | `purposes·age·occupation·employmentType·householdType·housingType·regionSido`. **성명·연락처 없음**(D41). 개인화 호출에만 |
| 8 | **사용자 질문(자연어)** | 요청 | 필수 | 실제 물어본 것. 답을 여기 맞춘다(D51) |
| 9 | **출력 스키마 지시** | 시스템 | 필수 | §4 JSON 강제 |
| 10 | **그라운딩 규칙** | 시스템 | 필수 | 인용 의무 + `source_id` 형식(§2) |
| 11 | **불확실성·면책 규칙** | 시스템 | 필수 | confidence + disclaimer |
| 12 | **언어·톤** | 시스템 | 필수 | 일반 시민 평이 한국어 |

**게이트(조립 전 검사):**
- `IMPACT/ACTION` → 프로필(7) 없으면 **해당 차원만 생략**(부분성공 `unmet`, [[service-api-spec]] §3.0) — 전체 거부 아님.
- `DIFF` → 기준선(5) 없으면 표시 후 진행.
- 요소 3·4 없음 = **법령 미해소** → 분석 거부(fail-closed).

---

## 2. 컨텍스트 조립 규칙

- **`source_id` 부여(인용 키, [[component-specs]] §1과 동일):**
  - 시행예정 조문: `LAW:{lawId}@{effectiveDate}:art:{no}`
  - 부칙: `LAW:{lawId}@{effectiveDate}:addenda:{no}` · 개정문: `…:amend`
  - 현행 기준선: `LAW:{lawId}:art:{no}`
  주입하는 모든 블록에 이 ID를 붙이고, 모델은 **claims에서 이 ID만 인용**할 수 있다.
- **우선순위(토큰 초과 시):** 코어 메타 > **변경 조문** > 기준선 diff > 부칙 > 개정문 근거 > LawFacts.
- **대형 법령:** `changed` 플래그로 이미 축소된다(실측 137→6조문). 옴니버스만 조문 단위 Map-Reduce(post-MVP).
- **캐싱(D51, [[component-specs]] §3.4):** ① 안정 프리픽스(시스템 가드레일 + 법령 사실 블록)에 **prompt caching**(`cache_control`) — 같은 법령의 여러 질의가 재사용. ② 답변 캐시는 **완전 동일 질의만**(`hash(질문)+law_ref+profileHash+prompt_version+revision`). 프로필·질문은 프리픽스 **뒤**(가변부).

---

## 3. 프롬프트 템플릿 (구조)

```
[SYSTEM]  ← 안정 프리픽스 (prompt caching · cache_control)
역할: 대한민국 시행 예정 법령 영향 분석가.
규칙:
 - 제공된 <context>의 source_id 근거만 사용. 외부 지식·추측 금지.
 - 모든 claim에 최소 1개 citation(source_id) 부착.
 - 불확실하면 confidence↓ + uncertainties 명시.
 - 법률 자문이 아닌 참고 정보.
 - 출력은 <output_schema> JSON만.

[CONTEXT]  ← 법령 단위 (프리픽스에 포함 → 캐시 재사용)
<law_core> lawId·title·status·effectiveDate·amendKind·ministry </law_core>
<changed_articles> [source_id: LAW:{lawId}@{efYd}:art:{no}] </changed_articles>
<baseline_diff> [현행 LAW:{lawId}:art:{no} ↔ 개정 대조] </baseline_diff>
<addenda> [시행일·경과조치, source_id] </addenda>
<amend_text> 개정문 (자구 변경 근거) </amend_text>

──────── 여기까지 안정 프리픽스 (캐시) ────────

[TASK]   차원 지시 (§5)
[PROFILE]  ← IMPACT·ACTION만. <profile>{purposes·age·occupation·가구·주거·시도}</profile>
[QUESTION] ← 사용자 자연어 질문 (답을 여기 맞춘다)
[OUTPUT_SCHEMA] §4
```

> **`<profile>`은 "수신자 정보"일 뿐 인용 가능한 source가 아니다**(D10 승계). `<context>`(법령)와 분리하고, `userId`는 넣지 않으며 속성만 직렬화한다(D41). 인구통계 정량 용도 금지(자기신고 표본, 대표성 없음).

---

## 4. 응답 포맷 (구조화 JSON)

Spring AI 구조화 출력(constrained decoding)으로 **스키마 강제**. [[component-specs]] §1.3 `ImpactResult`와 동일.

```jsonc
{
  "law_ref": "LAW:001809@2026-08-04",
  "command": "IMPACT",
  "summary": "한 문단 평이 요약",
  "claims": [
    { "statement": "전세 세입자는 사용검사 전 현장점검을 요청할 권리가 생긴다",
      "citations": ["LAW:001809@2026-08-04:art:49"], "confidence": 0.82 }
  ],
  "affected_profiles": ["전세 세입자", "입주예정자"],
  "impacts": [
    { "aspect": "주거", "direction": "영향 있음", "detail": "...", "citations": ["LAW:001809@2026-08-04:art:49"] }
  ],
  "actions": [
    { "what": "관리사무소에 현장점검 요청 가능 여부 확인", "deadline": "시행 후 사용검사 신청 건부터",
      "basis": ["LAW:001809@2026-08-04:addenda:3"] }
  ],
  "effective_info": { "status": "시행예정", "effective_date": "2026-08-04", "enforcement": "단계적" },
  "uncertainties": ["세부기준이 대통령령에 위임되어 확정 전"],
  "disclaimer": "본 정보는 법률 자문이 아닌 참고용입니다.",
  "meta": { "model": "claude-opus-4-8", "prompt_version": "0.2", "layer": "B" }
}
```

규칙:
- `claims[].citations` 비면 **무효 → 재생성**.
- 인용 `source_id`는 반드시 주입 context에 실재(§6).
- 차원별 미사용 필드는 생략 가능(스키마는 상위집합).

---

## 5. 차원별 프롬프트 변형

차원은 플래너가 질문에서 고르는 내부 단위다(D46). LOOKUP(발견)은 이 생성 콜이 아니라 `LawDiscovery` 검색이 담당.

| 차원 | 추가 필수 입력 | 핵심 출력 | 계층 |
|---|---|---|---|
| `SUMMARY` | 4 | summary, claims | A |
| `DIFF` | 4, 5 | claims(조문 대조), impacts | A |
| `IMPACT` | 4, 6, 7 | affected_profiles, impacts | B |
| `ACTION` | 4, 부칙, 7 | actions(기한·근거) | B |

---

## 6. 검증 훅 (응답 게이트)

1. **스키마 유효성** — JSON 파싱·필수 필드.
2. **인용 존재성(규칙)** — 모든 `citations`가 주입 context의 `source_id`에 실재하는가.
3. **인용 뒷받침(LLM-judge, 선택·post-MVP)** — 해당 조문이 statement를 실제로 지지하는가.
4. 실패 시: 재생성(≤N) → 계속 실패면 "근거 부족" 폴백, 환각 노출 금지.

---

## 7. 프롬프트 버저닝

- 모든 프롬프트에 `prompt_version` 부여, 응답 `meta`에 기록.
- 변경 시 새 버전 + 회귀 골든셋으로 비교(Evaluation Harness, post-MVP D36).
- 시스템/태스크/스키마를 별도 파일로 분리 관리(코드와 함께 버전관리).

---

## 8. 결정 필요 (Open)

- [ ] 호출당 토큰 예산 상한과 컨텍스트 자르기 임계값
- [ ] `confidence` 산정 방식(모델 자기보고 vs 후처리 보정)
- [ ] 검증 훅 3(LLM-judge) 적용 범위(보편 법령만 vs 전체)
- [ ] prompt caching 프리픽스 경계 최적화(§2·D51) — 어디까지 안정 프리픽스로 묶을지
