---
title: 법안 속성 카탈로그 (Bill Attributes)
status: Draft
version: 0.1
date: 2026-06-25
tags: [reference, bill, attributes, data-model]
related:
  - "components/component-specs.md"
  - "prompts/analysis-prompt-spec.md"
  - "adr/ADR-001-knowledge-store-sizing.md"
---

# 법안 속성 카탈로그 (Bill Attributes)

**관련:** [[component-specs|컴포넌트 스펙]] §1 도메인 모델 · [[analysis-prompt-spec|프롬프트 정의서]] · [[ADR-001-knowledge-store-sizing|ADR-001]]

법안에서 **무엇을, 어디서, 무엇에 쓰려고** 얻는지의 단일 참조. 서비스 가치(영향·대응안)를 만드는 속성을 빠짐없이 정리한다.

## 획득 3계층 (핵심 멘탈모델)

| 계층 | 출처 | 인용 가능? |
|---|---|---|
| 🟢 **A. API 직접 수집** | 열린국회/법제처/법령정보 OpenAPI 필드 | ✅ source |
| 🔵 **B. 의안 원문 파싱** | 원문(HWP/PDF) 안 제안이유·조문·신구조문대비표·부칙 | ✅ source |
| 🟡 **C. LLM 추출·추론** | A/B를 근거로 파생 | ❌ — 반드시 A/B `source_id` 인용 |

> **[[decision-log|D07]] 2계층 엔진과 연결:** A·B = `Bill`(원천 사실). C 중 *페르소나 무관*(영향범위·의무·벌칙·기한) = **`BillFacts`(Layer A 캐시)**. C 중 *페르소나별* = **`ImpactResult`(Layer B)**. **C를 `Bill`에 사실로 저장하지 않는다** — 검증 대상 추론이기 때문.

---

## 1. 식별·메타 (🟢 A)

| 속성 | 비고 | MVP |
|---|---|---|
| `billNo` 의안번호 | 식별·인용키 | ✅ |
| `title` 의안명 | | ✅ |
| `billKind` 제·개정 구분 (제정/일부개정/전부개정/폐지) | "무엇이 바뀌나" 프레이밍 | ✅ ★ |
| `lawType` 대상 법령종 (법률/시행령/시행규칙/조례) | 라우팅 | ✅ |
| `proposerType` 발의주체 (의원/정부/위원장) | 통과신호·신뢰 | ✅ |
| `proposers` 발의자(대표/공동) | 맥락 | ✅ |
| `committee` 소관위 | 도메인 힌트 | ✅ |
| `age` 대수·회기 / `sourceUrl` 원문링크 | 식별·인용 | ✅ |

## 2. 단계·시간축 (🟢A + 🔵B) — 서비스 차별점

| 속성 | 출처 | 쓰는 곳 | MVP |
|---|---|---|---|
| `stage` 현재 단계 | A | StageTracker·`stage_info` | ✅ |
| `stageHistory` 단계별 일자(회부/심사/의결/공포) | A | 추적 | ✅ |
| `procResult` 처리결과(가결/부결/폐기/대안반영폐기) | A | 유효성 | ✅ |
| `effectiveDate` 시행(예정)일 | B 부칙→A | ActionPlan·전체 | ✅ ★ |
| `effectiveRule` 시행규칙 ("공포 후 6개월" 등) | B 부칙 | ActionPlan 기한 산출 | ✅ ★ |
| `enforcementType` 시행유형(즉시/유예/단계적) | B/C | ActionPlan | ✅ |
| `expiryDate` 일몰/유효기간 | B 부칙 | | △ |

## 3. 본문·조문 (🔵 B) — 그라운딩 핵심

| 속성 | 쓰는 곳 | MVP |
|---|---|---|
| `proposalReason` 제안이유 + `mainContents` 주요내용 | ImpactSummary | ✅ ★ |
| `fullText` 전문 | 컨텍스트 | ✅ |
| `articles[]` 조문(no/title/text/`changeType`) | 전체·조문 인용 | ✅ ★ |
| 신구조문대비표 (개정 전후 대조) | **MVP의 diff 원천** (현행법 baseline 생략하므로 핵심) | ✅ ★ |
| `addenda` 부칙 (시행일/경과조치/적용례/특례) | ActionPlan·PersonaImpact | ✅ ★ |
| `delegationClauses` 위임조항 ("대통령령으로 정한다") | 영향이 하위법령에 위임 → `uncertainties` 표기 | ✅ ★ |

## 4. 영향 파생 (🟡 C → `BillFacts`, Layer A 캐시)

PersonaImpact/ActionPlan을 실제로 굴리는 값. **모든 항목은 조문 `source_id` 인용 필수.**

| 속성 | 쓰는 곳 | MVP |
|---|---|---|
| `impactScope` 영향범위(보편/도메인특정/소수) | triage·라우팅 ([[triage-policy|분류 기준]]) | ✅ |
| `affectedDomains` 영향 도메인·업종 | triage·세그먼트 매칭 | ✅ |
| `entityTypes` 영향 주체(개인/사업자/법인/기관) | 세그먼트 적용 판정 | ✅ |
| `obligations` 신규 의무(신고/등록/허가/납부) | ActionPlan·impacts | ✅ ★ |
| `rights` 신규 권리/혜택 | impacts | ✅ |
| `penalties` 벌칙·과태료·제재 | impacts (시민에게 결정적) | ✅ ★ |
| `deadlines` 기한(적용시점/신고기한) | ActionPlan | ✅ ★ |
| `thresholds` 적용 기준(금액·연령·규모) | 세그먼트 적용 여부 | ✅ |
| `moneyEffects` 비용/세금/지원금 변화 | impacts | ✅ |

## 5. 관계·맥락 (🟢A/🟡C) — 대부분 후속

| 속성 | 비고 | MVP |
|---|---|---|
| `baselineLawId` 개정 대상 현행법 | LawDiff(후속), MVP=null | △ |
| `relatedBills` 유사·경합·대안 관계 | 맥락 | △ |
| `costEstimate` 비용추계서 / `committeeReview` 검토보고서 | 신뢰·쟁점·통과신호 | △ |
| `precedentRefs` 유사 과거법안·판례 | Precedent(RAG, 후속) | ✗ |

## 6. 신뢰·인용 (시스템)

`source_id`(조문별 인용키) · `retrievedAt`/`lastSeen`(신선도) · `revision`(해시) — ✅ ★

## 7. 통과 가능성 신호 (🟢 A — StageTracker 후속, MVP 밖)

`proposerCount` 공동발의자수 · `partyDistribution` 발의 정당분포 · `govBillFlag` 정부입법 여부 · `pastSimilarOutcome` 유사법안 처리이력

---

## 서비스 관점 통찰

1. **차별점은 §2·§4에 있다.** 시간축(시행일·시행규칙) + 행동 가능 파생(의무·벌칙·기한)이 "조회"를 "내가 뭘 해야 하나"로 바꾼다.
2. **위임조항(§3★)이 함정.** 실질 영향이 "~는 대통령령으로 정한다"로 시행령에 위임되면 법안만으로 단정 불가 → `delegationClauses` 감지 시 `uncertainties`에 명시(환각 방지).
3. **A/B는 사실, C는 추론.** §4 파생은 LLM이 뽑되 반드시 §2·§3의 `source_id`를 인용해야 검증 게이트 통과. C는 `Bill`이 아니라 `BillFacts`(Layer A)·`ImpactResult`(Layer B)에 둔다.

## 저장소(ADR-001) 영향 평가

`BillFacts` 및 확장 필드를 더해도 **[[ADR-001-knowledge-store-sizing|ADR-001]] 결정은 불변**이다.
- `BillFacts` ≈ 5KB/건 × 5만 ≈ **0.25 GB**, 확장 Bill 필드는 `fullText` 추정에 흡수. RDB 헤드룸 50GB 내.
- ADR-001 재평가 트리거는 *벡터 5~10M 초과 / 조례 확장* — 테이블·컬럼 추가가 아님. 단일 Postgres 내 **스키마 진화**일 뿐 저장기술·사이징 결정과 직교.
