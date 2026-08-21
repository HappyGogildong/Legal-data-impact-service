---
title: DiffBuilder — 클래스 스펙
status: Draft
date: 2026-08-19
tags: [component, pipeline, diff]
related: ["components/component-specs.md", "components/Normalizer.md", "reference/law-domain-basics.md", "adr/decision-log.md"]
---

# DiffBuilder

> 시행예정본의 **변경 조문**을 시행중본(기준선)과 대조해 **신설·삭제를 확정**하고 `Article.diffVsCurrent`(근거 텍스트)를 채운다. 코드: `pipeline/diff/DiffBuilder`. 스키마: [[component-specs]] §1.2.

## Responsibility
- **담당:** `changed=true` 조문을 기준선과 대조 → `changeType`(신설/삭제/개정/이동) **확정** + `diffVsCurrent` 렌더.
- **담당 안 함:** 조문 파싱·`changed` 플래그 판정([[Normalizer]]) · 기준선 fetch(`LawConnector`) · "바뀌었는가" 자체 판단(출처 플래그가 정답) · 변경 조문 *선별*(도메인 `Law.changedArticles()`).

## Collaborators
- 입력 도메인: `Law` · `Article`(pending 및 baseline).
- 계측: `ObservationRegistry`(선택 주입, 기본 `NOOP`) — `lia.diff` 타이머/span.
- 외부 시스템: **없음**(순수 인프로세스 변환).

## Contract
- `build(pending, baseline) → Law`
  - **전제:** `pending`≠null이고 조문에 `changed` 플래그가 채워져 있음. `baseline`=null 허용(제정 법령 = 현행본 없음, [[law-domain-basics]] §3).
  - **보장:** `changed=true` 조문만 `diffVsCurrent`·확정 `changeType`를 가진 **새 `Law` 사본**. 미변경 조문은 불변. `baseline`=null이면 변경 조문 **전부 신설**.

## Invariants
- 대상은 **`changed=true` 조문뿐**(비용 레버 — 주택법 137→6). 미변경 조문은 손대지 않음(`diffVsCurrent`=null 유지).
- **정렬키 = 조문번호**(D42). 이동(`이동`)은 옛 번호(`movedFrom`)로 기준선을 찾는다.
- **삭제는 in-place "삭제" 마커로 감지** — 시행중·시행예정본이 동일 스키마로 조문 전문을 주므로 삭제도 pending에 마커로 온다(팬텀 스캔 없음, "플래그가 정답").
- `Law`는 불변 record — 입력을 변형하지 않고 `withArticles`로 사본 반환.

## Error Handling
- `pending==null` → `IllegalArgumentException`(대조 대상 부재).
- 그 외 예외 없음 — 기준선 대응 조문이 없어도(획득 갭) 신설로 처리하고 깨지지 않는다.

## Side Effects
- **없음(순수 함수).**

## Design Constraints
- 시행중본↔시행예정본이 **동일 스키마로 조문 전문**을 주는 계약에 의존(신구조문대비표 파싱 안 함).
- 복수 시행예정본의 기준 시점은 [[decision-log|D43]](가장 이른 시행일). 순차 체이닝은 post-MVP.
