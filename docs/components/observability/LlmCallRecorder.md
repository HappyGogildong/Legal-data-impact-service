---
title: LlmCallRecorder — LLM·임베딩 호출 기록
status: Draft
date: 2026-11-11
tags: [component, observability, cost]
related:
  - "eval/rag-evaluation-framework.md"
  - "backend/cost-policy.md"
  - "backend/observability.md"
---

# LlmCallRecorder

> 유료 모델 호출 1회마다 토큰 사용량을 `llm_calls` 에 한 행 남긴다(D83 Token & Cost). 비용은 저장하지 않고 단가 표 `llm_prices` 로 조회할 때 계산한다.

설계 배경은 [[rag-evaluation-framework]] §2.4·§8. 이 문서는 기록 경로(`com.lia.core.observability.llm`)와 표의 계약만 적는다.

## Responsibility

- 담당: Opus(요약·변경점·영향·대응, 대응 재생성)·Haiku(분류)·임베딩(법령 발견, 색인) 호출마다 목적·모델·토큰·종료 사유·성공 여부를 남긴다. 분석 엔진의 재시도는 묶음 키와 시도 번호, 인용 검증 통과 여부로 묶는다.
- 담당 안 함: 비용 계산(조회 시 `llm_call_costs` 뷰), 지연 시간(Prometheus `gen_ai.*`), 품질 평가([[OfflineEvaluator]]), 질의 이벤트(다음 단계).
- 남기지 않는 것: `user_id`, 질문, 답변 내용, 프로필, 법령ID — 비용 분석에 필요 없다(최소수집 D41).

## Collaborators

- `UsageCaptureAdvisor` — `ChatClient` 의 advisor. 응답의 `ChatResponseMetadata`(모델·`Usage`)와 첫 본문 generation 의 종료 사유를 호출자가 넘긴 `UsageCapture` 에 담는다. `.entity()` 변환 전에 담으므로 JSON 변환이 실패해도 사용량은 남는다.
- `AnalysisEngine` — 시도마다 기록한다(목적·묶음·시도·인용 검증 결과·근거 블록 수·요청 차원).
- `SpringAiQueryTranslator` — 분류 호출마다 기록한다.
- `RecordingEmbeddingModel` — Spring AI `EmbeddingModel` 을 감싼다. `PgVectorStore` 의 색인·검색 임베딩이 모두 `call()` 하나를 지나므로 여기서 기록한다.
- 외부 시스템: Postgres(`llm_calls`, `llm_prices`).

## Contract

- **목적(`LlmPurpose`)** — `CLASSIFY` · `DISCOVERY_EMBED` · `INDEX_EMBED` · `SUMMARY` · `DIFF` · `PERSONALIZED`(첫 Layer B, 영향·대응을 한 번에) · `ACTION_REVISION`(상황 반영 대응 재생성). 분석 목적은 요청 차원에서 정한다(SUMMARY·DIFF 그대로, IMPACT → `PERSONALIZED`, ACTION → `ACTION_REVISION`).
- **임베딩 목적** — 호출 단위로는 색인과 검색을 가를 수 없어 실행 모드로 정한다. `lia.llm-calls.embedding-purpose`: 기본 `DISCOVERY_EMBED`, 배치 프로파일 `INDEX_EMBED`.
- **`AnalysisEngine` 기록 규칙** — `analyze` 1회 = 묶음 키 1개. 시도마다 한 행.
  - `succeeded` — 추론기가 예외 없이 결과를 냈는가(API 오류·JSON 변환 실패는 false).
  - `accepted` — 인용 검증을 통과해 응답에 쓰였는가. 실패한 호출은 비운다.
  - `requested` — 첫 Layer B 만, 사용자에게 나갈 차원(`IMPACT`·`ACTION`·`IMPACT,ACTION`). 호출은 늘 둘 다 만든다.
  - `context_sources` — 주입한 근거 블록 수. 근거 블록당 입력 토큰의 분모.
- **토큰** — `input_tokens` 는 공급자의 입력 토큰(Anthropic 은 캐시 읽기·쓰기를 뺀 값), `output_tokens` 는 thinking 포함 출력. 캐시 토큰은 공급자가 줄 때만. 응답이 없으면 비운다.
- **모델** — 응답 메타데이터의 모델 ID. 응답이 없으면 추론기가 요청한 모델, 그것도 없으면 `unknown`.

## Invariants

- 기록은 본 처리의 결과를 바꾸지 않는다. 기록 실패·대기열 초과는 삼키고 `lia.llm.calls.dropped{reason}` 를 올린다.
- 행에 사람을 식별하거나 내용을 되살릴 값이 없다.
- 단가는 시점별로 쌓는다. 비용 = 호출 시점(서울 날짜)에 유효한 단가(`effective_from` 이 그날 이하인 것 중 최신) × 토큰 / 100만. 단가가 바뀌어도 기록을 고치지 않는다.

## Error Handling

- 추론기·번역기·임베딩 호출이 던지면 실패 행을 남기고 같은 예외를 그대로 다시 던진다.
- DB 쓰기 실패는 경고 로그(목적·모델만)와 `reason=write_failed` 로 끝난다.

## Side Effects

- `llm_calls` INSERT. 웹은 비동기(단일 스레드·크기 제한 대기열 `lia.llm-calls.queue-capacity`, 넘치면 버리고 `reason=queue_full`, 종료 시 남은 것을 몇 초 비운다). 배치·평가는 한 번 돌고 끝나는 프로세스라 동기로 쓴다(`lia.llm-calls.async: false`).

## Persistence Contract

- `llm_calls`(V16) — 호출 1회 1행. 인덱스 `(called_at)` · `(purpose, called_at)` · 부분 인덱스 `(group_key) WHERE group_key IS NOT NULL`. 당분간 지우지 않는다(행이 작다) — 쌓이는 양을 본 뒤 보존 기간을 정한다.
- `llm_prices`(V16) — PK `(model, token_kind, effective_from)`, `token_kind` ∈ `input`·`output`·`cache_read`·`cache_write`. 처음엔 비어 있다. 공식 가격표를 보고 사람이 넣는다([[cost-policy]]).
- `llm_call_costs`(뷰) — `llm_calls` 행마다 토큰 종류별 단가를 붙인 `cost_usd`. 단가가 없는 토큰 종류가 있으면 `cost_usd` 는 NULL(0으로 꾸미지 않는다)이고 `priced = false`.
- 운영 `grafana_reader` 에는 이 셋의 SELECT 만 준다([[cloud-infra]] 런북 11).

## Design Constraints

- 웹 요청 경로에 DB 쓰기를 더하지 않으려 비동기로 쓴다. 버린 건수는 지표로 드러난다 — 월 1회 청구액 대조([[rag-evaluation-framework]] §8)에서 차이를 설명할 수 있게.
- 대시보드 `LIA · Token & Cost` 는 이 표만 읽는다.
