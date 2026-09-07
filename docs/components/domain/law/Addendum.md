---
title: Addendum — 클래스 스펙 (도메인)
status: Reference
date: 2026-09-01
tags: [component, domain, law]
related: ["components/component-specs.md", "components/domain/law/Law.md", "components/normalize/Normalizer.md", "adr/decision-log.md"]
---

# Addendum

> 법령 **부칙 1조**(값 객체) — 시행일·경과조치·적용례·특례. [[Law]]가 보유. 스키마 SSOT: [[component-specs]] §1.2.

## Responsibility
- **담당:** 부칙 데이터 보유 + 종류 판정(`kindOf(title)` → `Kind`).
- **담당 안 함:** 이번 개정분 필터([[Normalizer]]) · 시행규칙 추출([[Normalizer]]이 시행일 조항에서 `Law.effectiveRule`·`enforcementType` 도출).

## Contract
- `kindOf(title)` — 제목으로 `Kind`(시행일·경과조치·적용례·특례·기타) 분류. 분류 불가면 `기타`.

## Invariants
- **anemic**(외부 권위 사실, 패키지 규칙 4) — 필드 불변식 없음.

## Behavior (함정 — 코드가 못 말하는 것)
- **부칙은 제정 이후 이력 전체가 온다** — 실측 42개 중 이번 개정분은 1개. `promulgateNo == 법령.공포번호`로 필터하지 않으면 **10년 전 경과조치를 이번 개정으로 오인**한다([[Normalizer]] `parseAddenda`).
- **`Kind.시행일` 부칙이 시행 시점의 근거** — `Law.effectiveClause()`가 이걸 찾아 `effectiveRule`("공포 후 6개월…")·`enforcementType`(단서조항 유무로 즉시/유예/단계적)을 도출. `ActionPlan`의 기한이 여기 의존.
- 인용키 `LAW:{lawId}@{efYd}:addenda:{no}`는 [[Law]] `addendumSourceId(this)`가 생성(포맷 권위는 Law).

## Schema
→ [[component-specs]] §1.2. 재기술하지 않는다.
