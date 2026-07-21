---
title: Triage 정책 (영향범위 분류·라우팅)
status: Draft
version: 0.1
date: 2026-06-28
tags: [reference, triage, classification, routing, impact-scope]
related:
  - "reference/bill-attributes.md"
  - "components/component-specs.md"
  - "adr/decision-log.md"
  - "adr/ADR-001-knowledge-store-sizing.md"
---

# Triage 정책 — 영향범위 분류·라우팅

**관련:** [[bill-attributes|법안 속성]] §4 (`impactScope`) · [[component-specs|컴포넌트 스펙]] (BillFacts) · [[ADR-001-knowledge-store-sizing|ADR-001]] (캐시·비용)

법안을 분석 처리에 넣기 전, **"누구에게 얼마나 영향이고, 그래서 얼마나 깊게 분석할까"** 를 가려내는 분류·라우팅 정책. 분류 결과(`BillFacts.impactScope` + `affectedDomains`)가 *어떤 커맨드·페르소나로, 어느 깊이·모델·캐시 정책으로* 돌릴지를 결정한다.

> **MVP 위치:** triage **스테이지(차등 라우팅)는 MVP 밖**. MVP는 `impactScope`·`affectedDomains`를 *메타데이터로 추출만* 하고, 4종 커맨드를 6개 개인 세그먼트에 **균일 적용**한다. 본 문서는 *기준을 미리 고정*해 BillFacts 추출 프롬프트 설계를 명확히 하기 위함. (관련: [[decision-log|D24~D27]])

---

## 1. 분류 체계 (impactScope)

| 클래스 | 정의 | 예 |
|---|---|---|
| **보편 (universal)** | 전 국민·다수 계층에 *넓고 얕게* 영향 | 소득세법, 근로기준법, 주택임대차보호법, 4대보험, 민법 |
| **도메인특정 (domain)** | 특정 산업·직군·자격군에 *좁고 깊게* 영향 | 자본시장법, 의료기기법, 특정 산업 규제, 전문자격사법 |
| **소수 (niche)** | 매우 좁은 집단·예외 케이스 | 특정 기관 설치법, 극소 대상 특례 |

- `impactScope`는 **단일 라벨**(택1). `affectedDomains`는 **다중 라벨**(주거·세제·근로…)로 공존.
- 한 법안이 보편+도메인 성격을 동시에 가지면 → **주된 영향**으로 `impactScope`를 정하고, 세부는 `affectedDomains`·`entityTypes`로 표현.

---

## 2. 분류 신호 (Signals)

🟡 C계층 추론으로 판정하되, 아래 신호를 근거로 삼는다. 모든 판정은 근거 조문/메타의 `source_id`를 인용([[bill-attributes]] §6).

| 신호 | 보편 ← | → 도메인특정 | → 소수 | 출처 |
|---|---|---|---|---|
| **소관위원회** (강한 단서) | 기재위·법사위·행안위 등 광범위 | 정무위(금융)·복지위(의료) 등 특정 산업위 | — | 🟢 A `committee` |
| **`entityTypes` 영향 주체** | `개인` 포함·전 계층 | `사업자/법인/기관` 위주 | 특정 소집단 | 🟡 C |
| **`affectedDomains` 폭** | 다수 도메인 | 1~2개 좁은 도메인 | 극소 | 🟡 C |
| **적용 대상(`thresholds`)** | 전 국민·전 가구 등 보편 | 특정 업종·자격·규모 조건 | 매우 좁은 조건 | 🟡 C (적용범위 조문 근거) |
| **인구 커버리지** | 세그먼트 다수 합산 高 | 소수 세그먼트 | 단일·미미 | Nemotron 분포 *(post-MVP)* |

---

## 3. 판정 룰 (Decision Rule)

순서대로 평가(우선 매칭):

1. **적용 대상이 전 국민/전 가구**이고 `entityTypes`에 `개인`이 넓게 포함 + `affectedDomains` 광범위 → **보편**
2. `entityTypes`가 `사업자/법인/기관` 위주이거나, **특정 업종·자격·규모 조건(`thresholds`)** 으로 적용 대상이 한정 → **도메인특정**
3. 적용 대상이 **극소 집단/예외** → **소수**
4. **불확실(저 confidence)** → **보편으로 처리**(더 넓게 분석) + `uncertainties` 표기
   - 근거: 시민 대상 서비스에서 *영향 누락*이 *추가 비용*보다 나쁘다. fail-open은 "넓게"로.

- 1차 단서는 **소관위 + entityTypes + 적용대상 보편성**. 인구 커버리지(Nemotron)는 post-MVP 보조 신호.
- 산출: `BillFacts.impactScope` + `confidence` + 근거 `citations`.

---

## 4. 라우팅 (분류 → 처리) — *post-MVP*

| | 보편 | 도메인특정 | 소수 |
|---|---|---|---|
| 페르소나 | **다수 세그먼트** 개인화(6개 전부) | **관련 세그먼트만** + (후속) 기업/기관 **엔티티 프로파일** | 최소 |
| 커맨드 깊이 | 요약+diff+영향(×N)+대응안 | + 규제 **컴플라이언스 깊이**, (선택) B2B 표면 | 요약+diff 위주 |
| 분석 시점 | **선제 precompute + 적극 캐시** | 온디맨드 | **lazy 온디맨드** |
| 모델 | 강모델, (선택) Generator-Critic | 강모델 | 비용 최소 |
| 검증 | LLM-judge 인용 뒷받침 적용 | 적용 | 규칙 검증만 |

→ 보편=선제·캐시, niche=lazy 가 [[ADR-001-knowledge-store-sizing|ADR-001]]의 **실질 운영비 레버**.

---

## 5. MVP에서 실제로 하는 것 / 안 하는 것

| | MVP | post-MVP |
|---|---|---|
| `impactScope`·`affectedDomains` 추출 | ✅ 메타데이터로 추출(BillFacts) | — |
| 분류 기반 **차등 라우팅** | ❌ — 4종 커맨드 × 6 세그먼트 **균일** | ✅ §4 라우팅 |
| 기업/기관 **엔티티 프로파일** | ❌ | ✅ |
| 인구분포 **triage 가중치** | ❌ | ✅ |
| 선제 precompute·차등 캐시 | ❌(요청 시 캐시만) | ✅ |

---

## 6. Open

- [ ] 소관위 → 보편/도메인 매핑 사전(어느 위원회가 어느 성향인지)
- [ ] `confidence` 임계값(보편 fail-open 발동 기준)
- [ ] 인구 커버리지 신호 산식(Nemotron `population_weight` 합산 규칙)
- [ ] 엔티티 프로파일 스키마(도메인 법안용, [[bill-attributes]] §통찰 참고)
