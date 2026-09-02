---
title: SourceAnalyzer — 클래스 스펙
status: Draft
date: 2026-08-22
tags: [component, pipeline, resolver]
related: ["components/component-specs.md", "architecture/v0.8-pending-law-corpus.md", "reference/law-domain-basics.md"]
---

# SourceAnalyzer

> 사용자 입력 → **어떤 시행예정 법령인가**를 해소(resolve). 분석가가 아니라 *식별자(resolver)* — 입력 내용을 사실로 받지 않는다(fail-closed). 코드: `pipeline/resolve/SourceAnalyzer`. 상태 스키마: [[component-specs]] §4 #2.

## Responsibility
- **담당:** 법령명/모호 자연어 → 실재 시행예정 법령 해소. **4상태**(RESOLVED/AMBIGUOUS/NOT_FOUND_YET/UNVERIFIED) 판정. 복수 시행예정본 → **가장 이른 본으로 해소 + 나머지 `alternatives`**(D43).
- **담당 안 함:** 분석·생성(입력을 사실로 받지 않음) · 본문 fetch(`LawConnector`) · 저장소 조회 *구현*(`LawLookup` 포트 뒤).

## Collaborators
- 아웃바운드 포트: **`LawLookup`**(정확 매칭) · `semanticSearch`(의미검색, 선택). 현재 `LawConnector` 어댑터 → 적재 후 Law Store+Vector Index로 **구현만 교체**(무수정).
- 계측: `ObservationRegistry`(선택, `NOOP`) — `lia.resolve{state}`.
- 외부 시스템: 없음(포트 뒤).

## Contract
- `resolve(input) → ResolutionResult`
  - **전제:** 임의 문자열(null 허용 → 미해소로 귀결).
  - **보장:** 정확히 4상태 중 하나. **`RESOLVED`만 `analyzable()`=true**. 미해소는 절대 `resolved`를 담지 않고 안내 문구 필수(생성자 강제). 출처 장애도 예외로 새지 않고 fail-closed 상태로 떨어진다.

## Behavior (해소 3단계)
1. **법령명 정확·퍼지 매칭**(`LawLookup`, 토큰 유사도) — 단일 강매칭→`RESOLVED` / **같은 `lawId` 복수→가장 이른 본 `RESOLVED`+`alternatives`**(D43) / 다른 법령 복수·약매칭→`AMBIGUOUS`.
2. **의미검색**(`pending` ns, [[RAGIndexer]]) — 1 실패 시 모호 서술을 후보화(주입된 경우).
3. **fail-closed 판정** — 못 찾으면: 법령스러우면 `NOT_FOUND_YET`, 아니면 `UNVERIFIED`.
- 임계: `confident=88`(단정), `ambiguous-min=60`(후보).

## Invariants
- **resolver ≠ analyzer** — "어떤 법령인가"만 판정, 데이터는 신뢰 출처 원문에서만.
- 4상태 불변식은 **타입이 강제**(`ResolutionResult` 생성자): 非RESOLVED면 `resolved=null`, 미해소는 안내 문구 필수, `alternatives`는 RESOLVED 전용([[decision-log|D23]]).

## Error Handling
- 조회 예외(출처 장애)도 **빈 결과로 흡수** → `NOT_FOUND_YET`/`UNVERIFIED`. 스택트레이스가 사용자에게 새지 않고, 없는 결과를 지어내지도 않는다.

## Side Effects
- **없음**(순수 판정 + 포트 조회).

## Design Constraints
- **fail-closed** — 미등록(`NOT_FOUND_YET`) vs 허위(`UNVERIFIED`)를 구분해 안내를 다르게(D23): "아직 없는 법" ≠ "지어낸 법".
- **포트 격리** — 저장소가 생겨도 `SourceAnalyzer`는 무수정, `LawLookup` 구현만 교체([[v0.8-pending-law-corpus]] §3.2).

## 의존 / 후속
- 포트: `LawLookup` → (현재) [[SourceConnector|LawConnector]] · (예정) Law Store + Vector Index `pending` ns
- 게이트 소비: `QueryDispatcher`/Orchestrator(#8) — `RESOLVED`만 통과
