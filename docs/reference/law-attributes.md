---
title: 법령 속성 카탈로그 (Law Attributes)
status: Draft
version: 0.2
date: 2026-08-02
tags: [reference, law, attributes, data-model]
related:
  - "components/component-specs.md"
  - "components/SourceConnector.md"
  - "prompts/analysis-prompt-spec.md"
  - "adr/ADR-001-knowledge-store-sizing.md"
---

# 법령 속성 카탈로그 (Law Attributes)

**관련:** [[component-specs|컴포넌트 스펙]] §1.1 도메인 모델 · [[SourceConnector|커넥터]] §MVP 본문 경로 · [[analysis-prompt-spec|프롬프트 정의서]] · [[ADR-001-knowledge-store-sizing|ADR-001]]

**시행 대기 법령**에서 *무엇을, 어디서, 무엇에 쓰려고* 얻는지의 단일 참조. 서비스 가치(영향·대응안)를 만드는 속성을 빠짐없이 정리한다.

> **v0.1 → v0.2 (D42·D44, 2026-08-02):** 문서 전체를 *의안(Bill)* 기준에서 **시행 대기 법령(Law)** 기준으로 다시 썼다. 의안번호·발의자·소관위·심사단계·통과 가능성 신호는 MVP 대상이 아니므로 제거했다(의안이 복귀하면 별도 문서로). 파일명도 `bill-attributes` → `law-attributes`.

## 획득 2계층 (핵심 멘탈모델)

| 계층 | 출처 | 인용 가능? |
|---|---|---|
| 🟢 **A. API 직접 수집** | 국가법령정보 `lawService.do` — 기본정보·조문·부칙·개정문·제개정이유 | ✅ source |
| 🟡 **C. LLM 추출·추론** | A를 근거로 파생 | ❌ — 반드시 A의 `source_id` 인용 |

> **🔵B(원문 파싱) 계층이 사라졌다.** v0.1에서는 조문·부칙·신구조문대비표를 HWP/PDF 원문에서 뽑아야 해서 별도 계층이었다. 시행 대기 법령은 **API가 조문 전문·부칙·개정문을 모두 주므로** B가 A에 흡수됐다([[SourceConnector]] §MVP 본문 경로). 그라운딩 가능한 사실이 늘고 파서 부담이 사라진 것이 D42의 실질 효과다.

> **[[decision-log|D07]] 2계층 엔진과 연결:** A = `Law`(원천 사실). C 중 *프로필 무관*(영향범위·의무·벌칙·기한) = **`LawFacts`(Layer A 캐시)**. C 중 *프로필별* = **`ImpactResult`(Layer B)**. **C를 `Law`에 사실로 저장하지 않는다** — 검증 대상 추론이기 때문.

---

## 1. 식별·메타 (🟢 A)

| 속성 | 비고 | MVP |
|---|---|---|
| `lawId` 법령ID | **버전 불변 식별자.** 시행중↔시행예정 연결키 | ✅ ★ |
| `mst` 법령일련번호 | 버전별 본문 조회용. **연결키로 쓰지 말 것** | ✅ |
| `title` 법령명(한글) | 사용자 입력 해소의 1차 매칭 대상 | ✅ |
| `status` 현행연혁코드 (시행중/시행예정) | MVP 코퍼스 선별 | ✅ ★ |
| `amendKind` 제개정구분 (제정/일부개정/전부개정/타법개정/폐지) | "무엇이 바뀌나" 프레이밍 | ✅ ★ |
| `lawType` 법종구분 (법률/대통령령/총리령/부령) | 라우팅·위임 관계 | ✅ |
| `ministry` 소관부처 | 도메인 힌트 · 문의처 안내. ⚠️ 응답이 중첩 객체 | ✅ |
| `promulgateNo` 공포번호 | **부칙 필터 키** — 이력 중 이번 개정분 선별 | ✅ ★ |
| `promulgateDate` 공포일자 / `sourceUrl` | 식별·인용 | ✅ |

## 2. 시간축 (🟢 A) — 서비스 차별점

의안과 달리 **시행일이 확정**돼 있다. `ActionPlan`("언제까지 무엇을 하라")이 단정적으로 성립하는 근거가 여기다.

| 속성 | 출처 | 쓰는 곳 | MVP |
|---|---|---|---|
| `effectiveDate` 시행일자 | 기본정보 | ActionPlan·전체 | ✅ ★ |
| `effectiveRule` 시행규칙 문구 | 부칙 제1조 | 기한 산출·설명 | ✅ ★ |
| `enforcementType` 시행유형(즉시/유예/단계적) | 부칙 단서조항 유무 | ActionPlan | ✅ ★ |
| `articleEffectiveDate` 조문별 시행일 | 조문단위 | 단계적 시행 시 조문별 안내 | ✅ |
| `expiryDate` 일몰·유효기간 | 부칙 | | △ |

> 실측 예(주택법): *"공포 후 6개월이 경과한 날부터 시행한다. 다만, 제57조제2항제7호의 개정규정은 공포한 날부터 시행한다"* → `enforcementType = 단계적`.

## 3. 본문·조문 (🟢 A) — 그라운딩 핵심

| 속성 | 쓰는 곳 | MVP |
|---|---|---|
| `amendReason` 제개정이유 (= 개정이유 및 주요내용) | ImpactSummary | ✅ ★ |
| `amendText` **개정문** — 자구 단위 개정 지시문 | **LawDiff 근거.** 신구조문대비표를 대체 | ✅ ★ |
| `articles[]` 조문(번호/제목/본문/시행일) | 전체·조문 인용 | ✅ ★ |
| `changed` **조문변경여부** | **LawDiff 대상 선별** — 실측 137개 중 6개 | ✅ ★ |
| `movedFrom`/`movedTo` 조문이동 | 조문 번호 재배치 추적 | ✅ |
| `addenda` 부칙 (시행일/경과조치/적용례/특례) | ActionPlan·PersonaImpact | ✅ ★ |
| `delegationClauses` 위임조항 ("대통령령으로 정한다") | 영향이 하위법령에 위임 → `uncertainties` 표기 | ✅ ★ |
| `fullText` 전문 (조문 병합) | 컨텍스트 | ✅ |

> **파싱 함정 2건:** ① `조문내용`만 읽으면 본문이 빈다 — 실제 내용은 `항 → 호 → 목` 중첩이라 **재귀 병합 필수** ② 부칙은 제정 이후 **이력 전체**(실측 42개)가 오므로 `부칙공포번호 == 공포번호`로 걸러야 한다.
> **`개정문` 정규식 파싱 금지** — 실측에서 타법 인용 조문번호를 오탐하고 벌칙·과태료 조문을 누락했다. 대상 선별은 `changed` 플래그가 정답이고, `개정문`은 사람이 읽을 근거 텍스트로만 인용한다.

## 4. 영향 파생 (🟡 C → `LawFacts`, Layer A 캐시)

PersonaImpact/ActionPlan을 실제로 굴리는 값. **모든 항목은 조문 `source_id` 인용 필수.**

| 속성 | 쓰는 곳 | MVP |
|---|---|---|
| `impactScope` 영향범위(보편/도메인특정/소수) | triage·라우팅 ([[triage-policy|분류 기준]]) | ✅ |
| `affectedDomains` 영향 도메인·업종 | triage·프로필 매칭 | ✅ |
| `entityTypes` 영향 주체(개인/사업자/법인/기관) | 적용 판정 | ✅ |
| `obligations` 신규 의무(신고/등록/허가/납부) | ActionPlan·impacts | ✅ ★ |
| `rights` 신규 권리/혜택 | impacts | ✅ |
| `penalties` 벌칙·과태료·제재 | impacts (시민에게 결정적) | ✅ ★ |
| `deadlines` 기한(적용시점/신고기한) | ActionPlan | ✅ ★ |
| `thresholds` 적용 기준(금액·**연령**·규모) | 프로필 적용 여부 — `age` 정수 대조(D41) | ✅ |
| `moneyEffects` 비용/세금/지원금 변화 | impacts | ✅ |

## 5. 관계·맥락

| 속성 | 비고 | MVP |
|---|---|---|
| `baselineLawId` diff 기준선 | **같은 `lawId`의 시행중본.** `target=law&ID=` 로 조회 | ✅ ★ |
| 동일 `lawId`의 **다른 시행예정본** | 시행 대기 개정이 복수일 수 있다 → 기준 시점 정책 미정(**D43**) | ⚠️ |
| 상·하위 법령 관계 (법률↔시행령↔시행규칙) | 위임조항 추적 | △ |
| `precedentRefs` 유사 선례·판례 | Precedent(RAG, 후속) | ✗ |

## 6. 신뢰·인용 (시스템)

`source_id`(인용키) · `retrievedAt`/`lastSeen`(신선도) · `revision`(해시) — ✅ ★

인용키는 **시행일까지 포함**한다: `LAW:{lawId}@{effectiveDate}:art:{no}`. 같은 법령ID에 시행예정본이 복수일 수 있어(D43) 법령ID만으로는 조문이 특정되지 않기 때문이다. 형식 전체는 [[component-specs]] §1 참고.

---

## 서비스 관점 통찰

1. **차별점은 §2·§4에 있다.** 확정된 시간축(시행일·시행규칙) + 행동 가능 파생(의무·벌칙·기한)이 "조회"를 "내가 뭘 해야 하나"로 바꾼다. 의안 시절엔 시행일이 미정이라 이 축이 성립하지 않았다.
2. **위임조항(§3★)이 함정.** 실질 영향이 "~는 대통령령으로 정한다"로 하위법령에 위임되면 법률만으로 단정 불가 → `delegationClauses` 감지 시 `uncertainties`에 명시(환각 방지). **시행 대기 법령에서도 그대로 유효하다** — 공포됐다고 위임 내용이 정해진 것은 아니다.
3. **A는 사실, C는 추론.** §4 파생은 LLM이 뽑되 반드시 §2·§3의 `source_id`를 인용해야 검증 게이트를 통과한다. C는 `Law`가 아니라 `LawFacts`(Layer A)·`ImpactResult`(Layer B)에 둔다.
4. **`changed` 플래그가 비용 레버다.** 분석 대상을 전체 조문에서 변경 조문으로 좁히면(실측 137→6) 토큰이 20분의 1이 된다. ADR-001이 지목한 "실질 운영비 동인 = 런타임 LLM 호출"에 직접 작용한다.

## 저장소(ADR-001) 영향 평가

**[[ADR-001-knowledge-store-sizing|ADR-001]] 결정은 불변**이며, D42로 오히려 여유가 커졌다.

- MVP 코퍼스가 *의안 5만 건 가정*에서 **시행예정 899건**(2026-08-02~2027-12-31 실측)으로 줄었다. `LawFacts` ≈ 5KB/건 기준 **5MB 미만**.
- 부피의 주축은 여전히 **diff 기준선용 시행중 법령 본문**이며, 이는 ADR-001의 현행법 추정(~0.4GB) 안이다.
- ADR-001 재평가 트리거는 *벡터 5~10M 초과 / 조례 확장*이지 테이블·컬럼 추가가 아니다. 단일 Postgres 내 **스키마 진화**일 뿐 저장기술·사이징 결정과 직교한다.
