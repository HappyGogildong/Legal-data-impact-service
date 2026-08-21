---
title: 클래스 스펙 문서 규약 (템플릿)
status: Reference
date: 2026-08-19
tags: [component, template, convention]
---

# 클래스 스펙 문서 규약

`docs/components/`의 클래스 스펙은 **코드·javadoc이 *못* 말하는 것**만 담는다.

- **필드/시그니처 미러링 금지** — 코드가 SSOT다. 문서는 *왜·계약·불변식·부작용·제약*에 집중.
- **스키마는 [[component-specs]] §1 참조** — per-file 문서에서 도메인 스키마를 다시 적지 않는다.
- **이력 금지** — 현재 상태만 서술한다(이력은 [[decision-log]] D번호).

## 공통 Core (모든 대상 클래스)

```
# {ClassName}
> 한 줄 역할.

## Responsibility
- 담당: …
- 담당 안 함: …            ← 경계를 분명히(가장 중요)

## Collaborators
- 협력자/포트: …           ← 의존 방향
- 외부 시스템: DB·API·…    (없으면 "없음")

## Contract
- 핵심 공개 메서드의 **계약만**(전제 → 보장, 실패 시). 전체 시그니처 미러링 아님.

## Invariants
- 객체가 항상 만족하는 조건(생성자가 강제하는 것 포함).

## Error Handling
- 무엇을·언제 던지나 / 어디까지 전파하나.

## Side Effects
- DB·API·캐시·이벤트. **없으면 "없음(순수 함수)".**

## Design Constraints
- 성능·동시성·설계 제약 (해당 시).
```

## 타입별 확장 (그 타입일 때만 추가)

| 타입 | 추가 섹션 |
|---|---|
| Entity / Record | `## Schema`(→ [[component-specs]] §1 참조) · `## State Transition`(있으면) |
| Repository / Store | `## Persistence Contract` · `## Query·Index` · `## Transaction/Lock` |
| Service / Orchestrator | `## Business Flow` · `## Transaction Boundary` · `## Business Rules` |
| Controller / API | `## HTTP Contract` (Method·URL·Request·Response·Status) |
| DTO | `## Schema` · `## Validation` · `## Mapping` |
| Connector / Adapter | `## External API Contract` · `## 봉투·오류 규약` |
| Pipeline / Transformer | `## Behavior`(핵심 단계·함정 — 알고리즘 의도이지 시그니처 미러링 아님) · `## 실측 검증`(실 데이터로 확인한 사실, 있으면) |

## 적용 범위

- **핵심 클래스**(행위·계약 있는 것): Core 전체 + 해당 타입 확장.
- **records / DTO**: 경량 — `Schema`(참조) + `Invariants`만.
- **신규 컴포넌트**: **구현 전 spec-first** — 계약을 먼저 못 박아 구현을 이끈다.
