---
title: RAGIndexer — 클래스 스펙 (설계 확정 · 구현 착수)
status: Draft
date: 2026-08-27
tags: [component, pipeline, rag, indexing]
related: ["components/component-specs.md", "components/Embedder.md", "components/ChunkStore.md", "components/LawStore.md", "components/IngestService.md", "reference/embedding-benchmark.md", "adr/decision-log.md"]
---

# RAGIndexer

> **시행예정 코퍼스**를 임베딩해 벡터 인덱스([[ChunkStore]])에 적재. 검색 *전에* 오프라인으로. 관련: [[component-specs]] §4 #4 · [[decision-log|D30·D55]].
>
> **이번 증분 (2026-08-27 확정):** 색인 대상 = **변경 조문 + `amendReason`(제개정이유) 요약**. 요약의 최종 소스는 D55의 LawFacts지만 미구현이라, 지금 있는 `amendReason`으로 법령단위 요약 벡터를 만든다(LawFacts 랜딩 시 소스만 교체). 과대 조문 분할 = **문자수 휴리스틱**(토크나이저 의존 없음). 저장은 **전용 [[ChunkStore]]**(PgVectorStore 래핑, **정본 단위 replace**로 재색인 = 현재 상태).
>
> **임베딩 주체 (2026-08-27 정정):** RAGIndexer는 **임베딩을 직접 하지 않는다.** Spring AI `PgVectorStore.add(Document)`가 `content`를 **내부에서** 설정된 `EmbeddingModel`로 임베딩한다. RAGIndexer는 **Document(content+metadata+source_id)만 조립**해 [[ChunkStore]]에 넘긴다. "적재·검색 동일 모델"은 PgVectorStore의 add/search가 같은 EmbeddingModel을 쓰므로 자동 보장된다. ([[Embedder]] 포트는 이 경로 밖 — 설정 핀·eval 유틸.)

## Responsibility
- **담당:** 시행예정 법령의 **변경 조문**(article) + **요약**(summary)를 청킹 → `source_id` 부여 → [[ChunkStore]] replaceVersion(정본 단위 완전 교체). 재색인=현재 상태.
- **담당 안 함:** **임베딩**(PgVectorStore가 [[ChunkStore]] 안에서 수행) · 벡터 저장 *구현*([[ChunkStore]]) · 검색([[SourceAnalyzer]]/dispatch) · 조문 텍스트 병합([[Normalizer]]). **시행중 조문은 색인하지 않는다**(기준선은 diff용 정확 fetch).

## Collaborators
- [[ChunkStore]](Chunk replaceVersion — 내부에서 PgVectorStore가 content 임베딩).
- 입력: [[Normalizer]] 산출 `Law`(변경 조문 텍스트·`amendReason`). (후속) Layer A 파생 요약·LawFacts.
- 호출: [[IngestService]] `ingestPending` 배치가 `store` 뒤에 `index` 호출. 외부 시스템: 없음(저장·임베딩은 ChunkStore/PgVectorStore).

## Contract
- `index(Law pending) → void` — 시행예정 `Law`를 청킹해 [[ChunkStore]]에 적재(임베딩은 저장 시 내부 수행).
  - **전제:** `pending`은 시행예정본, 변경 조문(`changed=true`) 텍스트 확보.
  - **보장:** `pending` 네임스페이스에 조문·요약 chunks가 `source_id` 메타와 함께 색인. **정본 재색인 = 현재 상태**(그 `(lawId, efYd)`의 청크 집합이 현재 법령과 정확히 일치 — stale 없음).

## Behavior (색인 단계)
1. **대상 선별** — `Law.changedArticles()` + `amendReason` 요약. (전문·미변경 조문·시행중본 제외 — 저장·노이즈 절감.)
2. **청킹**(D55) —
   - **조문 단위**: 변경 조문 1개 = 1 청크. **과대 조문**(문자수 > 임계 `MAX_CHARS`)만 분할(하위 청크 `art:{no}#k`).
   - **구조 기반 분할**: Normalizer가 항→호→목을 `\n`로 병합하므로 줄 = 항/호/목 단위다. **줄 경계를 지키며 그리디 패킹**해 단위 중간을 자르지 않는다(법령 RAG 품질). 인접 청크는 직전 1줄 오버랩으로 문맥 보존. 한 줄(항)이 그 자체로 한도를 넘는 예외에만 문자 분할 폴백.
   - ⚠️ **문자수 ≠ 토큰수** — `text-embedding-3-small`(cl100k)에서 한국어는 음절당 토큰이 많아(대략 2~3토큰/음절) 6000자면 8191토큰을 넘길 수 있다. 그래서 임계를 **한국어 기준 보수적으로 2500자**(`MAX_CHARS`)로 둔다. 정밀 제한이 필요하면 jtokkit(cl100k) 토큰 카운트로 교체(D48 — 지금은 의존성 회피).
   - **요약**: `amendReason` = 법령 단위 1 청크(과대 시 동일 분할).
3. **`source_id` 부여** — 조문 `LAW:{lawId}@{efYd}:art:{no}` · 요약 `LAW:{lawId}@{efYd}`. **시행일 포함**(복수 시행예정본, D43). 메타: `source_id·lawId·efYd·kind(article|summary)·namespace=pending·changed·articleNo?`.
4. **적재** — [[ChunkStore]] `replaceVersion(lawId, efYd, chunks)`: **정본 단위 완전 교체**라 재색인 시 사라진 청크(분할 수 감소·조문 삭제)가 stale로 남지 않는다. **임베딩은 ChunkStore 내부**에서 PgVectorStore가 `content`로 수행 — RAGIndexer는 벡터를 다루지 않는다.

## Invariants
- **단일 네임스페이스 `pending`** — 시행중 조문을 섞지 않는다(현행과 곧 바뀔 내용이 뒤섞이면 검색 노이즈, D30).
- **`source_id` 동반 필수** — 검색 결과가 곧 인용 근거(그라운딩 게이트 입력, 인용 무결성).
- **적재·검색 동일 임베딩 모델** — PgVectorStore가 add/search에 같은 `EmbeddingModel`을 써 자동 보장(벡터공간 일치).
- **변경 조문만**(비용 레버 137→6) — 전문 임베딩 안 함.

## Error Handling
- Embedder/ChunkStore 실패 → 적재 배치가 재시도. 부분 실패 시 `source_id` 멱등이라 재실행 안전. 예외는 감싸지 않고 전파(fail-fast).

## Observability
- 별도 `lia.*` 스팬 없음 — 임베딩은 Spring AI `gen_ai.*`([[Embedder]]), 벡터 저장은 Spring AI `db.vector.client.operation`(ChunkStore/PgVectorStore) 내장 계측에 위임.

## Side Effects
- **벡터 인덱스 쓰기**([[ChunkStore]]) · [[Embedder]] 통한 **외부 API 호출**.

## Design Constraints
- **적재 ≠ 검색** — 오프라인 배치·무상태(D29/D40). 검색은 런타임([[SourceAnalyzer]]).
- **법령 전문 임베딩 안 함** — 해소된 법은 context에 통째로 들어가고, 변경 조문만 다루면 되므로(137→6) 전문 벡터 실익이 작다.
- **모델 변경 = 전 코퍼스 재색인**([[Embedder]] 확정 후 고정).
- **`store()`는 임베딩 프리** — 적재 정본 조립(IngestService.store)에 임베딩을 섞지 않는다(무키 통합테스트 보호). 색인은 `ingestPending` 배치 단계에서만.

## 검증
- **단위:** Fake `ChunkStore`(replaceVersion된 Chunk 캡처) → 청크 개수·`source_id` 형식·**항 경계 분할(중간 안 자름)**·문자 폴백·요약 청크·미변경 조문 제외·정본 스코프·메타 검증. **순수 로직, 임베딩·DB 무관**.
- **통합:** [[ChunkStore]] 통합테스트(Testcontainers pgvector)로 replaceVersion→search 라운드트립 · **재색인 stale 제거** · 정본 스코프 격리 확인(임베딩 포함).
