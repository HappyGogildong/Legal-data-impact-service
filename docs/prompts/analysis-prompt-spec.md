---
title: LLM 분석 엔진 — API 호출 프롬프트 정의서
status: Draft
version: 0.1
date: 2026-06-25
tags: [prompt, llm, analysis-engine, spec]
related: ["architecture/v0.3-no-video-internal-mcp.md", "adr/ADR-001-knowledge-store-sizing.md"]
---

# LLM 분석 엔진 — API 호출 프롬프트 정의서 (v0.1 Draft)

**관련:** [[v0.3-no-video-internal-mcp|아키텍처 v0.3]] §3.4 해석 엔진 · [[ADR-001-knowledge-store-sizing|ADR-001]] (캐시/비용)

## 0. 목적·전제

외부 **foundation 모델 API**(강모델, 예: Claude Opus/Sonnet)를 호출해 입법 영향 분석을 생성할 때, **프롬프트를 어떻게 구성하고 응답을 어떤 형식으로 받을지**를 규정한다.

전제(앞선 결정 재확인):
- 모델은 **외부 API**. 자체/경량 모델 학습 없음. (티어링은 선택적 비용 최적화)
- **RAG/RDB = 컨텍스트 공급원.** 파운데이션 모델이 모르는 *최신·구체 법령 데이터*를 호출 시점에 주입한다.
- **모든 주장은 조문 ID로 역추적**되어야 한다(그라운딩·인용검증). 파라미터 기억에 의존 금지.
- 출력은 **사람용 텍스트가 아니라 검증·렌더 가능한 구조화 JSON**으로 받는다.

---

## 1. 프롬프트 입력 계약 (필요 조건) ★

호출 1건의 프롬프트를 만들기 위해 **무엇이 갖춰져야 하는가**. 오케스트레이터(`AnalysisPipeline`)가 아래를 조립해 넣는다. `requirements()`/`supports()`와 직접 연결된다.

| # | 입력 요소 | 출처 | 필수성 | 설명 |
|---|---|---|---|---|
| 1 | **시스템 역할·정책** | 시스템(상수) | 필수 | "입법 영향 분석가. 근거 없는 주장 금지. 법률자문 아님." 등 불변 가드레일 |
| 2 | **태스크 지시** | 시스템(커맨드별) | 필수 | 이 호출이 수행할 작업(요약/영향/대응안 등) |
| 3 | **법안 코어 메타** | RDB | 필수 | `billNo, title, stage, effectiveDate, committee, proposers` |
| 4 | **법안 조문 본문** | RDB | 필수 | `articles[]{no, text, changeType(신설/개정/삭제)}` + **부칙(시행일·경과조치·적용례)** |
| 5 | **현행법 기준선 + diff** | RDB/RAG | 조건부 필수 | 영향·diff 계열에 필수. `baselineLaw` 조문 + 개정 전후 대조 |
| 6 | **유사 선례/과거 법안** | RAG | 선택 | precedent 비교 시 주입 |
| 7 | **페르소나/세그먼트** | Persona Store (Nemotron 파생) | **Layer B 필수** | 개인화 호출에만. Layer A(사실층)에는 불필요 |
| 8 | **출력 스키마 지시** | 시스템 | 필수 | §4 JSON 스키마 강제 |
| 9 | **그라운딩 규칙** | 시스템 | 필수 | 인용 의무 + `source_id` 형식(§2) |
| 10 | **불확실성·면책 규칙** | 시스템 | 필수 | confidence 부여 + disclaimer |
| 11 | **언어·톤** | 시스템 | 필수 | 일반 시민 평이 한국어 (도메인 호출은 전문 톤 허용) |
| 12 | **토큰 예산·우선순위** | 오케스트레이터 | 필수 | 컨텍스트 초과 시 자르기 규칙(§2) |

**게이트(전제 조건 검사):** 조립 전에 검증한다.
- `PersonaImpact/ActionPlan` → 요소 7 없으면 실행 거부.
- `LawDiff/ImpactSummary` → 요소 5 없으면 거부(또는 baseline 미존재 표시).
- 요소 3·4 없으면 모든 분석 호출 거부(법안 미해소 상태).

---

## 2. 컨텍스트 조립 규칙

- **source_id 부여(인용 키):**
  - 법안 조문: `BILL:{billNo}:art:{no}`
  - 부칙: `BILL:{billNo}:addenda:{no}`
  - 현행법: `LAW:{lawId}:art:{no}`
  - 선례: `PREC:{billNo}`
  주입하는 모든 텍스트 블록에 이 ID를 붙이고, 모델은 **claims에서 이 ID만 인용**할 수 있다.
- **우선순위(토큰 초과 시):** 코어 메타 > 변경 조문(changeType≠없음) > 해당 현행법 diff > 부칙 > 비변경 조문 > 선례.
- **대형 법안:** 조문 수가 임계 초과면 변경 조문 위주로 선별하거나 조문 단위 Map-Reduce로 분할(아키텍처 아이디어 C).
- **캐시 키:** Layer A(사실층)= `billNo + bill_revision + prompt_version`(페르소나 무관). Layer B= 위 + `segment_id`.

---

## 3. 프롬프트 템플릿 (구조)

```
[SYSTEM]
역할: 대한민국 입법 영향 분석가.
규칙:
 - 제공된 <context>의 source_id 근거만 사용. 외부 지식·추측 금지.
 - 모든 주장(claim)에 최소 1개 citation(source_id) 부착.
 - 불확실하면 confidence를 낮추고 uncertainties에 명시.
 - 법률 자문이 아니라 참고 정보임.
 - 출력은 <output_schema> JSON만. 그 외 텍스트 금지.

[TASK]  ← 커맨드별 (§5)
예) "이 법안이 아래 persona에게 미치는 영향과 대응안을 분석하라."

[CONTEXT]
<bill_core> ... </bill_core>
<bill_articles> [source_id별 블록] </bill_articles>
<current_law_diff> [source_id별 블록] </current_law_diff>
<persona> {segment 속성: 연령·직업·지역·가구 등} </persona>   ← Layer B만
<precedents> ... </precedents>                                 ← 선택

[OUTPUT_SCHEMA]
§4의 JSON 스키마.
```

> 페르소나는 Nemotron-Personas-Korea에서 파생한 **세그먼트 속성**만 넣는다(취미·여행 등 정책 무관 서사는 제외). 합성 페르소나가 법적 근거를 오염시키지 않도록 `<persona>`는 "수신자 정보"일 뿐 인용 가능 source가 아님을 명시.

---

## 4. 응답 포맷 (구조화 JSON)

function calling / constrained decoding으로 **스키마 강제**.

```jsonc
{
  "bill_ref": "BILL:2210001",
  "command": "PersonaImpactCommand",
  "summary": "한 문단 평이 요약",
  "claims": [
    {
      "statement": "임차인의 계약갱신 청구 가능 기간이 연장된다",
      "citations": ["BILL:2210001:art:3", "LAW:001234:art:6"],
      "confidence": 0.78          // 0~1
    }
  ],
  "affected_segments": ["임차 가구", "청년 1인가구"],
  "impacts": [
    { "aspect": "주거 비용", "direction": "감소", "detail": "...", "citations": ["..."] }
  ],
  "actions": [
    { "what": "갱신 청구서 제출", "deadline": "시행일+30일", "basis": ["BILL:2210001:addenda:1"] }
  ],
  "stage_info": { "stage": "위원회심사", "effective_date": "2026-09-01", "passage_note": "..." },
  "uncertainties": ["통과 여부 미확정", "하위법령 위임 사항 존재"],
  "disclaimer": "본 정보는 법률 자문이 아닌 참고용입니다.",
  "meta": { "model": "claude-...", "prompt_version": "0.1", "layer": "B" }
}
```

규칙:
- `claims[].citations` 비어 있으면 **무효 → 재생성**.
- 인용된 source_id는 반드시 주입된 context에 존재해야 함(§6 검증).
- 커맨드별로 사용하지 않는 필드는 생략 가능(스키마는 상위집합).

---

## 5. 커맨드별 프롬프트 변형

| 커맨드 | 추가 필수 입력 | 핵심 출력 필드 | 계층 |
|---|---|---|---|
| `ImpactSummaryCommand` | 5 | summary, claims | A/B 경계 |
| `LawDiffCommand` | 5 | claims(조문 대조), impacts | A |
| `StageTrackerCommand` | 3 | stage_info | A |
| `PersonaImpactCommand` | 5, 7 | affected_segments, impacts | B |
| `ActionPlanCommand` | 5, 7 | actions(기한·근거) | B |
| `SourceResolveCommand` | (입력=링크/텍스트) | 후보 법안 목록·신뢰도 | (전처리) |

---

## 6. 검증 훅 (응답 게이트)

1. **스키마 유효성** — JSON 파싱·필수 필드.
2. **인용 존재성(규칙)** — 모든 `citations`가 주입 context의 source_id에 실재하는가.
3. **인용 뒷받침(LLM-judge, 선택)** — 해당 조문이 statement를 실제로 지지하는가.
4. 실패 시: 재생성(최대 N회) → 계속 실패면 "분석 불가/근거 부족"으로 폴백, 환각 노출 금지.

---

## 7. 프롬프트 버저닝

- 모든 프롬프트에 `prompt_version` 부여, 응답 `meta`에 기록.
- 변경 시 새 버전 + 회귀 평가셋(아키텍처 아이디어 B의 평가 활용)으로 비교.
- 시스템/태스크/스키마를 별도 파일로 분리 관리(코드와 함께 버전관리).

---

## 8. 결정 필요 (Open)

- [ ] 추론 모델 픽 + 티어(강모델/triage 소형) 및 한국어 품질 벤치
- [ ] 호출당 토큰 예산 상한과 컨텍스트 자르기 임계값
- [ ] `confidence` 산정 방식(모델 자기보고 vs 후처리 보정)
- [ ] 세그먼트(persona) 스키마 확정 — Nemotron 26필드 중 정책 관련 부분집합 선정
- [ ] 검증 훅 3(LLM-judge) 적용 범위(보편 법안만 vs 전체)
