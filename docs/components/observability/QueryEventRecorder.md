---
title: QueryEventRecorder — 질의 이벤트 기록
status: Draft
date: 2026-11-11
tags: [component, observability, eval]
related:
  - "eval/rag-evaluation-framework.md"
  - "components/observability/LlmCallRecorder.md"
---

# QueryEventRecorder

> 분석 요청 1건·상황 반영 재생성 1건마다 결과의 모양(상태·경로·차원·못 채운 사유)을 `query_events` 에 한 행 남긴다(D83 2단계). 원문은 남기지 않는다.

지표 배경은 [[rag-evaluation-framework]] §2·§6.2. 토큰·재시도는 [[LlmCallRecorder]] 의 `llm_calls` 가 맡고, 법령별 조회 수는 `law_views_daily`(D66)가 맡는다 — 여기서 다시 세지 않는다.

## Responsibility

- 담당: 운영의 거부율·미해소 사유·차원별 못 채운 사유(저장본 없음 = Layer A 선계산 커버리지)·발견 후보 0건 비율·한도 도달·재생성 결과·로그인 비율의 원천 기록.
- 담당 안 함: 품질 판정(정답이 없다 — 표본 판정 몫), 토큰·비용, 지연 시간.
- 남기지 않는 것: 질문 원문, `user_id`, 프로필, 법령ID.

## Collaborators

- `AnalyzeUseCase` — 분석 요청마다 기록(익명 상한 초과·예외 포함).
- `ActionRevisionUseCase` — 재생성 요청마다 기록.
- `RecordWriter` — 웹 비동기 쓰기(대기열 초과·쓰기 실패는 삼키고 `lia.records.dropped{record=query_event}`).
- 외부 시스템: Postgres(`query_events`).

## Contract

- **`kind`** — `ANALYZE`(`POST /api/analyses`) · `ACTION_REVISION`(상황 반영 재생성).
- **`status`**
  - `ANSWERED` — 차원을 1개 이상 채웠다(발견은 후보 1건 이상, 재생성은 대응 방안을 냈다).
  - `NOT_ANSWERED` — 법령·경로는 정했지만 아무것도 채우지 못했다(사유는 `unmet`).
  - `UNRESOLVED` — 법령을 정하지 못했다(사유는 `resolution_state`).
  - `ANONYMOUS_LIMIT` — 익명 하루 상한(D81)에 걸려 분류 전에 막혔다.
  - `ERROR` — 예외로 끝났다(입력 오류 포함). 예외는 그대로 다시 던진다.
- **`resolution_state`** — `OFF_TOPIC`(법령 질문이 아님) · `AMBIGUOUS` · `NOT_FOUND_YET` · `UNVERIFIED`(없는 법 이름 등). 법령 질문이 아닌 경우는 플래너가 미해소 결과에 표시한다 — 응답 모양은 그대로다.
- **`route`** — `REFERENCE`(법령 지목) · `DISCOVERY`(주제 검색). 미해소·익명 상한·예외면 비운다. 재생성은 `REFERENCE`.
- **`primary_type`·`types`·`filled`** — 차원 이름, 목록은 `QueryType` 선언 순서로 쉼표 구분. `primary_type` 은 화면이 먼저 보여 줄 주 의도, `types` 는 채워야 할 차원 전체(못 채운 비율의 분모).
- **`unmet`** — 못 채운 차원 → `Unmet.Reason` 이름(JSON 객체). 없으면 `{}`.
- **`candidates`** — 발견 후보 수, 또는 비슷한 이름의 법 후보 수(`AMBIGUOUS`). 그 밖엔 비운다.
- **`article_named`** — 법령 지목 경로에서 조문을 지목했는가. 그 밖엔 비운다.
- **`profile_search`** — "내 프로필 기준으로 찾기"를 켰는가(첫 분석만).
- **`prompt_version`** — 응답 시점의 분석 프롬프트 버전.

## Invariants

- 기록은 응답을 바꾸지 않는다. 기록 실패는 응답을 실패시키지 않는다.
- 요청 1건 = 행 1개. 예외로 끝난 요청도 한 행(`ERROR`)이다.

## Side Effects

- `query_events` INSERT(웹 비동기).

## Persistence Contract

- `query_events`(V17). 인덱스 `(occurred_at)` · `(status, occurred_at)`. 보존 기간은 쌓인 양을 본 뒤 정한다(지금은 지우지 않는다).
- 운영 `grafana_reader` 에 SELECT 를 준다 — 사람을 식별할 값이 없다.
- 대시보드 `LIA · Requests` 가 이 표만 읽는다.
