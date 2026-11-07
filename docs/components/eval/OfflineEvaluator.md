---
title: OfflineEvaluator — 오프라인 회귀 평가 하네스
status: Draft
date: 2026-11-08
tags: [component, eval, regression]
related:
  - "eval/rag-evaluation-framework.md"
  - "adr/decision-log.md"
---

# OfflineEvaluator

> 정답 세트의 질문을 서비스와 같은 경로(`QueryPlanner` → `LawDiscovery`)로 돌려, 법령 찾기·조문·의도·거부 지표를 낸다(D83).

평가 설계와 지표 정의는 [[rag-evaluation-framework]] §2·§5. 이 문서는 `com.lia.core.eval` 의 오프라인 회귀 부분(`EvalCase`·`OfflineEvaluator`·`OfflineMetrics`·`RegressionGate`·`OfflineEvalRunner`)의 계약만 적는다.

## Responsibility

- 담당: 질문 1건 → 서비스 경로의 결과 요약(`CaseOutcome`), 결과 묶음 → 지표와 분모(`EvalReport`), 기준선과 비교한 회귀 판정, 운영 DB 에 대고 한 번 돌고 끝나는 실행.
- 담당 안 함: 답변 생성 품질(Opus), 저장본 평가, 운영 기록 집계 — 각각 표본 판정과 운영 배치의 몫이다. 결정적 지표(필수 근거 포함률·규칙 거부·버전 선택·낡은 저장본)는 해당 컴포넌트의 단위·통합 테스트가 맡는다.
- 담당 안 함: 문턱값 구간별 정답률 — `SourceAnalyzer` 가 점수를 밖으로 내주지 않아 후속.

## Collaborators

- `QueryPlanner`(번역은 실제 Haiku) · `LawDiscovery`(실제 임베딩·pgvector) — 서비스가 쓰는 그 빈을 그대로 쓴다.
- 외부 시스템: Anthropic(Haiku), OpenAI(임베딩), Postgres. 실행 1회에 문항 수만큼 Haiku, 주제 질문 수만큼 임베딩.

## Contract

- **`EvalCase`** — 정답 세트 1문항. `kind` 가 `ANSWERABLE` 이면 정답 법령이 1개 이상이어야 하고, `NO_MATCH`·`REFUSE` 면 정답 법령이 없어야 한다(생성자가 거부). `lawNamed` 는 질문이 법령 이름을 댔는지 — 지표를 나누는 기준이며, 플래너가 어느 경로를 골랐는지로 나누지 않는다(경로를 잘못 고른 문항이 분모에서 빠지지 않게).
- **`OfflineEvaluator.evaluate(query)`** — 프로필 없이, 명시적 법령 없이 `plan` 을 부른다.
  - `Unresolved` + `NOT_FOUND_YET`·`UNVERIFIED` → 거부.
  - `Unresolved` + `AMBIGUOUS` → 해소 후보 목록(순서 유지)을 후보로, 거부 아님.
  - `Planned` + 참조 → 해소된 법령 1개, 조문 번호, 차원.
  - `Planned` + 발견 → `LawDiscovery.discover` 후보 목록(순서 유지). 후보 0건은 거부가 아니라 "후보 없음".
- **`OfflineMetrics.score(cases, outcomes)`** — 지표마다 비율과 분모를 함께 낸다. 분모가 0인 지표는 싣지 않는다(없는 값을 0이나 1로 꾸미지 않는다).

| 지표 키 | 분모 | 방향 |
|---|---|---|
| `law.resolution` | `ANSWERABLE` + `lawNamed` | 높을수록 |
| `discovery.hit@3`, `discovery.mrr` | `ANSWERABLE` + 이름 없음 | 높을수록 |
| `discovery.irrelevant` | 위 문항들이 낸 후보 전체 | 낮을수록 |
| `article.match` | 정답에 조문이 있는 문항 | 높을수록 |
| `nomatch.candidate_rate` | `NO_MATCH` | 낮을수록 |
| `refusal.recall` | `REFUSE` | 높을수록 |
| `refusal.over` | `ANSWERABLE` | 낮을수록 |
| `intent.<차원>.precision`·`recall`·`f1`, `intent.macro_f1` | 정답에 차원이 있는 문항 | 높을수록 |

- **`RegressionGate.check(report, baseline)`** — 기준선의 각 지표에 대해 나빠진 방향으로 허용 오차를 넘으면 실패. 허용 오차는 설정값과 `1/분모` 중 큰 값이다(한 문항 차이로 실패하지 않게). 기준선에 없는 지표는 비교하지 않는다.
- **`OfflineEvalRunner`**(`eval` 프로파일) — 정답 세트 로드 → 평가 → 보고서 JSON 을 표준 출력에 → 기준선이 있으면 비교. 종료 코드 0=통과(기준선 없음 포함) · 1=실행 실패 · 3=회귀. 정답 세트·기준선 위치는 접두사 없으면 클래스패스, `file:` 이면 파일. 설정·색인 규모·문항 수가 기준선과 다르면 실패시키지 않고 경고한다.

## Invariants

- 평가는 서비스 경로의 빈을 그대로 쓴다. 평가 전용 검색·해소 구현을 두지 않는다.
- 보고서에는 지표 값, 분모, 발견 설정(`chunkFetch`·`maxCandidates`·`minSimilarity`), 색인된 법령 수, 문항 수가 함께 실린다 — 색인 규모가 다른 기준선과 비교하지 않으려면 사람이 이 값을 본다.

## Error Handling

- 정답 세트가 없거나 형식이 틀리면 실행 실패(1). 문항 하나의 평가 중 예외는 실행 전체를 실패시킨다 — 조용히 빼면 분모가 바뀐다.
- 기준선 파일이 없으면 비교하지 않고 통과(0)로 끝내며, 그 사실을 로그에 남긴다.

## Side Effects

- 유료 API 호출(Haiku·임베딩). DB 는 읽기만 한다. 파일을 쓰지 않는다 — 일회성 컨테이너가 지워져도 남도록 보고서는 표준 출력으로 낸다.

## Design Constraints

- 실제 기준선은 운영 전체 색인에서만 의미가 있다. 운영 DB 는 외부에서 접근할 수 없으므로 EC2 에서 같은 이미지로 수동 실행한다(배포 래퍼 연결은 정답 세트 작성 단계에서).
- 정답 세트는 공개 레포에 커밋된다 — 실제 사용자 질문을 넣지 않는다([[rag-evaluation-framework]] §7).
