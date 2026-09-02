---
title: Law — 클래스 스펙 (도메인)
status: Reference
date: 2026-09-01
tags: [component, domain, law]
related: ["components/component-specs.md", "components/domain/Article.md", "components/domain/Addendum.md", "components/ingest/Normalizer.md", "components/store/LawStore.md", "adr/decision-log.md"]
---

# Law

> 국가법령정보 **법령 1버전의 불변 스냅샷**(정본). MVP 유일 분석 모델(D42). 스키마 SSOT: [[component-specs]] §1.1 — 여기서는 코드가 못 말하는 것만.

## Responsibility
- **담당:** 정본 데이터 보유 + **도메인 질의**(변경 조문 선별·`source_id` 생성 권위·인용키 `ref`·전문 병합·시행 상태 판정) + 파생 사본(`withRevision`/`withBaseline`/`withArticles`).
- **담당 안 함:** 저장([[LawStore]]) · 정규화([[Normalizer]]) · 신설/삭제 **판정**([[DiffBuilder]]) · 분석([[AnalysisEngine]]). 데이터·질의만.

## Collaborators
- 보유: [[Article]]·[[Addendum]](값 리스트).
- 생성: [[Normalizer]]가 `RawLaw`→`Law`. 소비: [[DiffBuilder]]·[[ContextBuilder]]·[[RAGIndexer]]·[[LawStore]].

## Contract
- `changedArticles()` — `changed=true` 조문만(=이번 개정 대상, 비용 레버 137→6). **`changeType`(신설/삭제)이 아니라 `changed` 플래그 기준** — 판정은 DiffBuilder 몫.
- **`source_id` 생성 권위**(리뷰 P1, §1.3) — `sourceId(Article)`·`amendSourceId()`·`addendumSourceId(Addendum)`·요약 `ref()`. **포맷을 아는 유일한 곳** — 소비자는 문자열 조합 금지.
- `ref()` — 인용·캐시 키. **status로 `@efYd` 유무 결정**: 시행예정=`LAW:{lawId}@{efYd}`, 시행중=`LAW:{lawId}`(D43).
- `article(no)` — 조문번호 조회(현재 O(N) 선형 — 배치 성능 필요 시 인덱스화 검토).
- `withX(...)` — 불변이라 변경은 **사본 반환**(DiffBuilder가 조문 교체 등).

## Invariants
- **`lawId`·`effectiveDate` 필수**(생성자 강제) — 연결키·기한 산출 근거. 리스트 필드는 non-null(빈 리스트로 정규화).
- **정본 단위 = `(lawId, effectiveDate)`** — `lawId` 단독 아님(복수 시행예정본, D43). `mst`는 버전마다 달라 **연결키로 쓰지 않는다**.

## Design Constraints
- **외부 권위 사실이라 anemic**(패키지 규칙 4) — `Law`는 우리가 저작하지 않으므로 필드 불변식을 강제하지 않는다(필수 2개 제외). 우리 *판정*(ResolutionResult·AnalyzeRequest 등)만 불변식을 갖는다.
- **불변 스냅샷** — 통째로 읽고 쓰며(Aggregate Root·Repository 미도입) 변경은 `withX` 사본.
- 시행중본·시행예정본이 **같은 구조**(status로만 구분).

## Schema
→ [[component-specs]] §1.1(필드 카탈로그)·§1.3(source_id 형식). 여기서 재기술하지 않는다.
