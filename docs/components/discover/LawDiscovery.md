---
title: LawDiscovery — 법령 발견 (LOOKUP, pipeline 블록)
status: Draft
version: 0.1
date: 2026-10-29
tags: [component, pipeline, discovery, lookup, rag]
related: ["components/component-specs.md", "components/dispatch/QueryDispatcher.md", "components/store/ChunkStore.md", "components/store/LawStore.md", "components/web/AnalysisApi.md", "components/profile/UserProfile.md"]
---

# LawDiscovery (pipeline 블록, 법령 발견)

> 법령명을 모르는 질문(Discovery)에서 **질문과 맞닿은 시행예정 법령을 후보로 찾는** 결정론 검색 블록(D70). 질문을 임베딩해 `pending` 색인에서 유사 청크를 찾고, 법령 단위로 묶어 정본이 시행예정인 것만 카드로 돌려준다. **LLM 을 부르지 않고 분석도 하지 않는다** — 후보만 반환하고, 사용자가 고르면 원래 질문 + `lawRef` 로 다시 요청한다. 관련: [[QueryDispatcher]](호출) · [[ChunkStore]](유사도 검색) · [[LawStore]](정본·요약 저장본) · [[AnalysisApi]](응답 `discovery`) · [[component-specs]] §4 #8.

## 책임

- **담당**: 검색어 구성(질문 + 토글 시 프로필 라벨) → 유사도 검색 → 법령 단위 묶기 → 정본 시행예정 확인(fail-closed) → 후보 카드 조립(쉬운 요약 첫 문장·근거 라벨).
- **담당 안 함**: 자연어 번역·`LOOKUP` 으로 좁히기([[QueryPlanner]]) · 프로필 사용 가능 판정과 토글 해석(디스패처 — `Usable` 일 때만 프로필을 넘긴다, [[ProfileUseCase|PersonalizationGate]]) · 분석 생성·인용 검증([[AnalysisEngine]]) · 한도 차감·조회 집계(없음) · HTTP 매핑([[AnalysisApi]]).

## 구조 (컴포넌트) — `com.lia.core.pipeline.discover`

| 클래스 | 역할 |
|---|---|
| `LawDiscovery` | 블록 본체 — `discover(question, profile)`. 협력자(생성자 주입): `ChunkStore`(검색) · `LawSource`(정본 조회, [[LawStore]] 구현) · `LawSummaries`(쉬운 요약 저장본, D67) · `Settings`. Spring 애노테이션 없음 — `PipelineConfig` 의 `@Bean` 이 배선(기존 방식). |
| `LawDiscovery.Settings` (record) | 설정값 묶음 — `chunkFetch`·`maxCandidates`·`minSimilarity`·`snippetChars`·`candidateStatuses`(`lia.discovery.*`, 아래 표). |
| `DiscoveryResult` (record) | 결과 `{ candidates: List<DiscoveredLaw>, profileApplied: boolean }`. 후보가 없으면 빈 목록. |
| `DiscoveredLaw` (record) | 후보 카드 `{ lawId, title, effectiveDate, ministry, summary?, summaryReady, matched }`. |
| `DiscoveredLaw.MatchedSource` (record) | 근거 칩 `{ sourceId, label }` — 후보를 끌어올린 가장 유사한 청크의 출처(→ S05 그 조문). |

> `profileBlocked`(토글은 켰는데 프로필을 못 쓴 사유)는 이 블록이 모른다 — 프로필 판정은 디스패처의 몫이라 `DispatchResult.Discovery` 가 `found: DiscoveryResult` 옆에 싣는다([[QueryDispatcher]]).

## Contract

`discover(String question, Optional<UserProfile> profile) → DiscoveryResult`

- **전제**: `question` 비어있지 않음(web 가 선검증). `profile` 은 토글이 켜졌고 프로필이 `Usable` 일 때만 값이 있다(그 외 `Optional.empty()`).
- **보장**: 반환된 후보는 모두 **정본이 실재하고 상태가 `candidate-statuses`(시행예정)** 인 법령이다. 법령당 1건, 유사도 높은 순, 최대 `max-candidates`. `profileApplied` = 프로필 라벨이 실제로 검색어에 붙었는가.
- **userId 를 받지 않는다** — 검색어에 들어가는 것은 질문 원문과 `UserProfile.labelLines()`(닫힌 값 라벨)뿐이다(D41).

## 동작 (5단계)

1. **검색어 구성** — 기본은 질문 원문. `profile` 이 있으면 `질문 + "\n" + UserProfile.labelLines()`. 자유 입력·userId 는 절대 넣지 않는다.
2. **유사도 검색** — `ChunkStore.search(검색어, chunk-fetch, min-similarity)`. 코드에는 namespace 필터가 없다 — RAGIndexer 가 시행예정(pending) 버전만 색인에 적재하므로 결과적으로 시행예정 색인만 검색된다. 검색어는 [[ChunkStore]] 가 내부 임베딩한다(공유 임베딩 모델). 유사도가 `min-similarity` 미만인 청크는 저장소가 버린다. 메타(`lawId`·`efYd`)가 빠진 청크는 건너뛴다.
3. **법령 단위 묶기** — 청크를 `lawId` 로 묶어 법령당 가장 유사한 청크 1개만 남기고(그 청크가 근거 칩이 된다), 유사도 내림차순으로 정렬한다. 같은 법령의 다른 시행일본은 하나로 합쳐진다(TRANSITION 이 비시행예정 색인을 지우므로 실무상 드물다).
4. **정본 확인(fail-closed)** — 각 법령의 정본을 `LawSource.find(lawId, efYd)` 로 조회해 **있고 상태가 `candidate-statuses` 인 것만** 통과시킨다. 정본이 없거나(색인이 낡았을 때) 이미 시행됐거나 철회된 버전은 후보에서 뺀다.
5. **카드 조립** — 앞에서부터 `max-candidates` 건:
   - **`summary`** — 쉬운 요약 저장본(`LawSummaries.findCurrent`)의 `summary` **첫 문장**(`.`·`?` 뒤가 공백이거나 끝인 곳까지, 최대 120자, 넘으면 120자 + `…`), `summaryReady=true`. 저장본이 없으면 개정 이유(`amendReason`)의 공백 정규화 후 앞 `snippet-chars` 자(넘으면 + `…`), `summaryReady=false`(웹이 "요약 준비 중" 표기). 개정 이유도 없으면 `summary=null`.
   - **`matched.label`** — 조문 청크(메타 `articleNo`)면 `Article.labelOf(no)` + (정본 그 조문에 제목이 있으면 `" " + 제목`), 요약 청크면 `개정 이유`. `matched.sourceId` 는 `#k` 분할 접미사를 뗀 출처 키.
   - 법령 헤더(`title`·`effectiveDate`·`ministry`)는 정본에서 채운다.

## 설정값 (`lia.discovery.*`)

| 키 | 기본값 | 뜻 |
|---|---|---|
| `chunk-fetch` | 40 | 유사도 검색에서 가져올 청크 수(법령 단위로 묶기 전) |
| `max-candidates` | 10 | 응답에 담는 후보 법령 최대 수 |
| `min-similarity` | 0.3 | 이 값 미만 청크는 버린다 — **클라우드에서 전체 색인을 올린 뒤 실측해 조정**(로컬은 부분 색인이라 임시값) |
| `snippet-chars` | 80 | 요약 저장본이 없을 때 개정 이유에서 잘라 쓰는 글자 수 |
| `candidate-statuses` | `시행예정` | 후보로 허용하는 정본 상태 — 최근 시행 법령은 범위 밖(D42 유지), 넓히려면 이 값만 바꾼다 |

## Invariants

- **실재 시행예정만**: 색인에만 있고 정본이 없거나 상태가 허용 밖이면 후보가 아니다 — 지어내지 않는다(fail-closed).
- **userId 없음**: 검색어·로그·결과 어디에도 사용자 식별자를 싣지 않는다(D41). 프로필은 닫힌 값 라벨만.
- **LLM 없음**: 카드 본문은 저장본·정본 문장의 발췌뿐이다 — 생성·요약을 하지 않으므로 비용은 검색어 임베딩 1회.
- 결과는 같은 입력·같은 색인이면 같다(결정론).

## Error Handling

- 하한 이상 청크가 없거나 정본 확인에서 모두 탈락 → 예외가 아니라 **빈 후보 목록**(`profileApplied` 는 그대로).
- 메타가 누락된 청크(`lawId`·`efYd` 없음)는 **건너뛴다**(그 청크만).
- 임베딩 API 예외는 **전파**한다 — 디스패처가 삼키지 않고 시스템 오류로 흘려보낸다([[service-api-spec]] §4.1).

## Side Effects

- **없음 — 읽기만**: 색인 검색(+ 검색어 임베딩 API 1회)과 정본·요약 저장본 읽기. 쓰기·캐시·이벤트·한도 차감·조회 집계(D66)·Opus 호출 없음.
- 프로필 토글이 켜지면 프로필 라벨이 임베딩 API(OpenAI)로 나간다 — 동의 목적·처리 위탁 문구에 반영한다(D70 ④, 처리방침 버전은 정식 출시 때).

## 검증

- **단위**(`LawDiscoveryTest` 12, Fake `ChunkStore`·`LawSource`·`LawSummaries`): 검색어(질문만 / 프로필 라벨 포함·userId 없음) · 법령 단위 묶기(가장 유사한 청크가 근거) · 정본 없음·상태 불허 탈락(fail-closed) · `max-candidates` 상한 · 요약 첫 문장 규칙(마침표 뒤 공백·120자 절단) · 저장본 없음 → 개정 이유 앞부분 + `summaryReady=false` · 둘 다 없음 → `summary=null` · 근거 라벨(조문 제목 유무·요약 청크 → `개정 이유`·`#k` 접미사 제거) · 메타 누락 청크 건너뜀 · 검색 예외 전파(삼키지 않음).
- **유사도 하한**: `LawDiscovery` 는 검색 호출에 하한을 넘기고, 하한 미만 제외는 실 `ChunkStore` 의 `PgVectorChunkStoreIntegrationTest`(`유사도_하한`, Testcontainers pgvector, `skipped="0"` 확인)가 검증한다. 발견 파이프라인 전체를 관통하는 통합 테스트는 없다.
- **관련 계층**: 디스패처(`QueryDispatcherTest`)·플래너·유스케이스(`QueryPlannerTest`·`AnalyzeUseCaseTest` — 발견 후보 재요청에서 LOOKUP 제거 포함)·API(`AnalysisControllerTest`·`AnalysisResponseMapperTest`)·웹 단위·Playwright E2E(`discovery-flow`, 모의 API).
- **라이브 스모크**(만들기만, 옵트인 환경변수 `LIA_DISCOVERY_LIVE` — 유료 임베딩): 실제 임베딩으로 질문 → 후보 확인. 기본 test 에서 스킵.

## 의존 / 관련

[[QueryDispatcher]](호출·`DispatchResult.discovery`) · [[ChunkStore]](`pending` 색인 검색) · [[LawStore]](`LawSource`·`LawSummaries` 구현) · [[Embedder]](공유 임베딩 모델) · [[UserProfile]](`labelLines()`) · [[AnalysisApi]](응답 `discovery`) · [[QueryPlanner]](`LOOKUP` 으로 좁힘) · [[component-specs]] §4 #8 · 결정 D70(법령 발견 — 결정 로그 [[decision-log]]).
