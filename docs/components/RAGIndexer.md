---
title: RAGIndexer — 클래스 스펙 (spec-first)
status: Draft
date: 2026-08-22
tags: [component, pipeline, rag, indexing]
related: ["components/component-specs.md", "components/Embedder.md", "components/LawStore.md", "reference/embedding-benchmark.md", "adr/decision-log.md"]
---

# RAGIndexer

> **시행예정 코퍼스**를 임베딩해 벡터 인덱스([[LawStore]] `chunks`)에 적재. 검색 *전에* 오프라인으로. **구현 전 계약(spec-first)**. 관련: [[component-specs]] §4 #4 · [[decision-log|D30·D55]].

## Responsibility
- **담당:** 시행예정 법령의 **변경 조문**(article) + **요약/LawFacts**(summary)를 청킹 → [[Embedder]]로 임베딩 → [[LawStore]] `chunks` upsert. `source_id` 동반. 증분 재색인.
- **담당 안 함:** 임베딩 *생성*([[Embedder]]) · 벡터 저장 *구현*([[LawStore]]) · 검색([[SourceAnalyzer]]/dispatch) · 조문 텍스트 병합([[Normalizer]]). **시행중 조문은 색인하지 않는다**(기준선은 diff용 정확 fetch).

## Collaborators
- [[Embedder]](`mode=PASSAGE`) · [[LawStore]] `chunks`(PgVectorStore).
- 입력: [[Normalizer]](변경 조문 텍스트) · Layer A 파생(요약·LawFacts·affectedDomains).
- 외부 시스템: 없음(임베딩은 Embedder, 저장은 LawStore).

## Contract
- `index(pending, facts?) → void` — 시행예정 `Law` + (선택) `LawFacts`를 청킹·임베딩·적재.
  - **전제:** `pending`은 시행예정본, 변경 조문(`changed=true`) 텍스트 확보.
  - **보장:** `pending` 네임스페이스에 조문·요약 chunks가 `source_id` 메타와 함께 색인. 같은 `source_id` 재색인은 멱등(삭제-후-삽입).

## Behavior (색인 단계)
1. **대상 선별** — 변경 조문(`Law.changedArticles()`) + 요약/LawFacts. (전문·미변경 조문·시행중본 제외 — 저장·노이즈 절감.)
2. **청킹**(D55) —
   - **조문 단위**: 변경 조문 1개 = 1 청크. **과대 조문**(> `max_input_tokens`)만 **오버랩 분할**(하위 청크 `art:no#k`, 같은 출처 조문).
   - **요약**: 요약/LawFacts = 법령 단위 1~few 벡터.
3. **`source_id` 부여** — 조문 `LAW:{lawId}@{efYd}:art:{no}` · 요약 `LAW:{lawId}@{efYd}`. **시행일 포함**(복수 시행예정본, D43). 메타: `source_id·lawId·efYd·kind(article|summary)·namespace=pending·changed`.
4. **임베딩** — [[Embedder]] `mode=PASSAGE`(배치).
5. **적재** — [[LawStore]] `chunks` upsert(`source_id` 기준).
6. **증분** — `revision` 변동분만 재색인(해당 법의 chunks 삭제 후 재적재).

## Invariants
- **단일 네임스페이스 `pending`** — 시행중 조문을 섞지 않는다(현행과 곧 바뀔 내용이 뒤섞이면 검색 노이즈, D30).
- **`source_id` 동반 필수** — 검색 결과가 곧 인용 근거(그라운딩 게이트 입력, 인용 무결성).
- **적재·검색 동일 임베딩 모델**([[Embedder]] 공유) — 벡터공간 일치.
- **변경 조문만**(비용 레버 137→6) — 전문 임베딩 안 함.

## Error Handling
- Embedder/LawStore 실패 → 적재 배치가 재시도. 부분 실패 시 `source_id` 멱등이라 재실행 안전.

## Side Effects
- **벡터 인덱스 쓰기**([[LawStore]] `chunks`) · [[Embedder]] 통한 **외부 API 호출**.

## Design Constraints
- **적재 ≠ 검색** — 오프라인 배치·무상태(D29/D40). 검색은 런타임.
- **법령 전문 임베딩 안 함** — 해소된 법은 context에 통째로 들어가고, 변경 조문만 다루면 되므로(137→6) 전문 벡터 실익이 작다.
- **모델 변경 = 전 코퍼스 재색인**([[Embedder]] 확정 후 고정).
