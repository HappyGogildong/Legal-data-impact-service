---
title: Normalizer — 클래스 스펙
status: Draft
date: 2026-08-22
tags: [component, pipeline, normalizer]
related: ["components/component-specs.md", "reference/law-attributes.md", "components/SourceConnector.md", "reference/law-domain-basics.md"]
---

# Normalizer

> `RawLaw` → 표준 **`Law`(+`Article[]`·`Addendum[]`)** 정규화. 출처 API의 기벽을 여기서 끝낸다(Anti-Corruption Layer 안쪽 절반). 코드: `pipeline/normalize/Normalizer`. 스키마: [[component-specs]] §1.1.

## Responsibility
- **담당:** 헤더 매핑 · 조문 조립(`항→호→목` 재귀 병합) · 변경/이동 플래그 매핑 · `changeType`(개정/이동/없음) 판정 · 부칙 필터(공포번호)·분류 · 시행규칙·`enforcementType` 추출 · 위임조항 감지 · `revision` 해시.
- **담당 안 함:** 출처 API 호출(`LawConnector`) · **신설·삭제 확정**([[DiffBuilder]] — 기준선 필요) · `LawFacts` 파생(Layer A) · "바뀌었는가" 판단(출처 플래그가 정답).

## Collaborators
- 입력: `RawLaw`([[SourceConnector|LawConnector]] 산출) · 순수 파싱 헬퍼 `LawEnvelope`.
- 계측: `ObservationRegistry`(선택 주입, 기본 `NOOP`) — `lia.normalize`.
- 외부 시스템: **없음**.

## Contract
- `normalize(raw) → Law`
  - **전제:** `raw`≠null이고 본문 포함(`hasBody`). 아니면 `IllegalArgumentException`.
  - **보장:** 도메인 표준 `Law`. 실조문 본문이 채워짐(병합 결과), 부칙은 **이번 개정분만**(공포번호), `lawId`·`effectiveDate` 비어있지 않음(생성자 강제), `revision` **결정적**(같은 입력 → 같은 해시).

## Behavior (핵심 단계·함정)
1. **헤더 매핑** — `기본정보` → 헤더 필드. `소관부처` 등 중첩 객체는 평탄화(⚠️ 함정: 문자열 아님).
2. **조문 조립** — `조문내용` + `항→호→목` **재귀 병합**(⚠️ `조문내용`만 읽으면 제목 줄뿐). `조문여부≠"조문"`(장·절 제목)은 `isArticle=false`.
3. **변경/이동 매핑** — `조문변경여부`→`changed`, `조문이동이전/이후`→`movedFrom/To`, `조문시행일자`→`articleEffectiveDate`.
4. **`changeType`** — 개정/이동/없음까지만(신설·삭제는 [[DiffBuilder]]가 기준선 대조로 확정).
5. **부칙 필터·분류** — `부칙공포번호==promulgateNo`만 취함. 종류(시행일/경과조치/적용례/특례) 분류.
6. **시행규칙** — 부칙 제1조에서 `effectiveRule`, 단서("다만…")로 `enforcementType`(즉시/유예/단계적).
7. **위임조항 감지** — "~는 대통령령으로 정한다" → `delegationClauses`.
8. **`revision`** — `sha256(canonical(분석영향 필드))[:16]`. `lastSeen`은 제외.

## Invariants
- `changed` 플래그가 1급(비용 레버 — 137→6). **`개정문` 정규식 파싱 금지**(타법 인용 오탐·벌칙 조문 누락 실측).
- 부칙은 공포번호로 이번 개정분만(API는 제정 이후 이력 전체 제공).
- 파싱 실패 조문도 **드롭 금지** — `changeType="없음"`+원문 보존(그라운딩 가능성 유지).
- `revision`은 분석영향 필드만 해시 — 행정 메타 변동은 캐시 무효화하지 않음([[decision-log|D16]]).

## Error Handling
- 본문 없는 `RawLaw` → `IllegalArgumentException`. 개별 조문 파싱 실패는 예외가 아니라 **결손 보존**으로 흡수.

## Side Effects
- **없음(순수 변환).**

## Design Constraints
- **수집↔해석 분리** — 출처 API를 모른다(`RawLaw`만 받음). 출처 추가·응답 형식 변경이 커넥터에서 끝난다.
- **신구조문대비표 파싱 안 함** — 시행중·시행예정본이 동일 스키마로 조문 전문을 주므로 조문번호 직접 대조 + `개정문`이 자구 근거.

## 실측 검증 (주택법 `LAW:001809@2026-08-04`)
- 조문 137개(실조문 125, 변경 6) · 부칙 3 · 위임 237 · `revision` 결정적.
- 실조문 125개 **전부 본문이 채워짐**(빈 조문 0) — 항/호/목 병합이 실 응답에서 동작.
- 부칙 이력 전체에서 이번 개정분 3개 조항만 남음. `enforcementType=단계적`(단서조항).

## 의존 / 후속
- 입력: [[SourceConnector|LawConnector]] · 출력 적재: Law Store(RDB) · 후속: [[DiffBuilder]] · [[RAGIndexer]] · [[AnalysisEngine]]
