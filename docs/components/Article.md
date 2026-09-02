---
title: Article — 클래스 스펙 (도메인)
status: Reference
date: 2026-09-01
tags: [component, domain, law]
related: ["components/component-specs.md", "components/Law.md", "components/Normalizer.md", "components/DiffBuilder.md", "adr/decision-log.md"]
---

# Article

> 법령 **조문 1개**(값 객체). [[Law]]가 보유. 스키마 SSOT: [[component-specs]] §1.2 — 여기서는 코드가 못 말하는 것만.

## Responsibility
- **담당:** 조문 데이터 보유 + 라벨(`label()`) + diff 확정 사본(`withDiff`).
- **담당 안 함:** 본문 병합([[Normalizer]]) · 신설/삭제 판정([[DiffBuilder]]) · 인용키 생성([[Law]] `sourceId`).

## Contract
- `withDiff(confirmedType, diff)` — [[DiffBuilder]]가 `changeType`(신설/삭제/이동)을 **확정하고** `diffVsCurrent`(현행 대비 근거)를 채운 사본. 원본 Article은 판정 전 상태.

## Invariants
- **anemic**(외부 권위 사실, 패키지 규칙 4) — 필드 불변식 없음. `changed`·이동 등은 [[Normalizer]]가 출처값에서 채운다.

## Behavior (함정 — 코드가 못 말하는 것)
- **`text`는 `조문내용`만이 아니다** — 실측에서 `조문내용`은 제목 줄뿐이고 실제 내용은 `항 → 호 → 목` 중첩에 있어, [[Normalizer]]가 **재귀 병합한 결과**가 담긴다. (RAGIndexer의 과대 조문 분할은 이 병합된 `\n` 구조를 경계로 삼는다.)
- **`changed` 플래그가 diff·색인 대상의 정답** — `조문변경여부='Y'`. 개정문 정규식으로 변경 조문을 찾지 말 것(오탐·누락 실측). 비용 레버(137→6).
- **`changeType` 초기값 vs 확정값** — Normalizer 단계는 개정/이동/없음까지만. **신설·삭제는 기준선 없이는 알 수 없어** DiffBuilder가 `withDiff`로 확정한다.
- **`isArticle=false`** — 실조문이 아니라 장·절 제목 등 편제. `Law.realArticles()`가 걸러낸다.
- **`movedFrom`/`movedTo`** — 조문 이동(옛 제56조→제57조). 신설·삭제와 구분해 표시.

## Schema
→ [[component-specs]] §1.2. 재기술하지 않는다.
