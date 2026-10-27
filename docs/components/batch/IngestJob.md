---
title: IngestJob — 적재·RAG 색인 배치 잡 (작업 큐)
status: Draft
version: 0.2
date: 2026-10-19
tags: [component, batch, pipeline, ingest, queue, rag]
related: ["components/ingest/IngestService.md", "components/index/RAGIndexer.md", "components/store/LawStore.md", "components/store/ChunkStore.md", "components/connector/LawConnector.md", "backend/concurrency-and-reliability.md", "backend/deployment.md", "adr/decision-log.md"]
---

# IngestJob (적재·RAG 색인 배치 잡)

> 시행예정 법령을 주기적으로 수집해 **정본(`law_versions`)과 RAG 색인(`vector_store`)을 최신으로 유지**하는 오프라인 배치(D40·D62). 같은 이미지를 배치 프로파일로 띄워 **한 번 돌고 종료**하는 잡이고, 일은 **DB 작업 큐(`ingest_tasks`)**로 나눠 워커가 처리한다. 관련: [[IngestService]](조립) · [[RAGIndexer]](청킹) · [[LawStore]] · [[ChunkStore]] · [[concurrency-and-reliability]] §2.

## 설계 결정 (D62)
- **실행 모델 = 1회성 잡 프로파일** — 웹 서버 없이 실행 후 종료. 정기 실행은 외부 스케줄러(로컬: 호스트 cron + `docker compose run`, 클라우드: ECS 예약 작업 등), 수동 실행은 **명령줄 인자**. 온라인과 자원이 분리되고, 수동 실행용 관리자 API·인증이 필요 없다.
- **작업 큐 테이블** — 작업 종류 5개(`SYNC`·`INDEX`·`SUMMARIZE`·`TRANSITION`·`PURGE`)를 큐에 넣고 공용 워커 풀이 `FOR UPDATE SKIP LOCKED`로 가져간다. 단계별 재시도·재개·병렬 처리를 큐가 책임진다.
- **지나간 버전은 상태로 보존** — `과거`(대체된 시행중본)·`철회`(목록에서 사라진 시행예정본). 행은 남기고 RAG 색인에서만 뺀다(과거 답변의 인용 조문을 계속 되짚을 수 있게).
- **재시도 = 다음 실행에서**, 실행당 1회 시도. 일시적 오류는 3회 시도 후 격리(`DEAD`), 영구 오류는 즉시 격리.
- **단일 프로세스 + 소유권 확인, lease 없음** — advisory lock으로 배치는 한 번에 한 프로세스뿐이라, 새 실행이 잠금을 잡은 시점에 남은 `RUNNING`은 모두 죽은 프로세스의 것이다. 시간 기반 lease 대신 시작 시 일괄 복구하고, 완료·실패 기록은 `last_attempt_run`(실행 토큰)으로 소유권을 확인한다(§동시성).
- **시도 이력은 별도 테이블** — `ingest_tasks`는 작업의 *현재 상태*, `ingest_task_attempts`는 *시도마다의 이력*, `ingest_runs`는 *실행 단위*. `docker compose run --rm`은 컨테이너와 함께 로그를 지우므로 오류 이력은 DB에 남긴다.
- **쉬운 요약 선계산**(D67, #38 일부) — `SUMMARIZE` 작업. DIFF·LawFacts 선계산은 아직 범위 밖.

## Responsibility
- **담당:** 실행 진입·단일 실행 보장 · 목록 ⟷ 저장 상태 비교(탐색) · 작업 등록·분배·재시도·격리 · 법령 라이프사이클(시행 전환·철회) · 실행·시도 기록 · 중단된 실행 정리.
- **담당 안 함:** 출처 호출 형식([[LawConnector]]) · 정규화·diff·정본 조립([[IngestService]] `store`) · 청킹([[RAGIndexer]]) · 임베딩·벡터 저장([[ChunkStore]]) · Layer A 선계산(#38).

## Collaborators
- 재사용: [[LawConnector]](`listPending`·`fetchPending`·`fetchCurrent`) · [[IngestService]](`store`) · [[RAGIndexer]](`index`) · [[LawStore]] · [[ChunkStore]].
- 외부 시스템: 국가법령정보 API · 임베딩 API(OpenAI, ChunkStore 경유) · Postgres.
- 트리거: 외부 스케줄러(cron 등) — [[deployment]].

## 구조 (컴포넌트) — `com.lia.core.pipeline.batch`
| 컴포넌트 | 책임 |
|---|---|
| `IngestJobRunner` | `batch` 프로파일 진입점(`ApplicationRunner`). 명령 해석 → 잠금 → 잡 실행 → 종료 코드 |
| `IngestJob` | 실행 한 번의 흐름: 정리(중단된 실행·`RUNNING` 복구·보존 정리) → 탐색 → 큐 소진 → 기록 |
| `Discoverer` | 목록 전체 조회 ⟷ `law_versions` 비교 → 작업 등록. 목록 완주 여부 판정 |
| `TaskQueue` | `ingest_tasks`·`ingest_task_attempts`: 등록(중복 방지) · 가져가기(`SKIP LOCKED` + 시도 행) · 완료·실패(소유권 확인) · 격리 재투입 · 중단 복구 |
| `SyncHandler` · `IndexHandler` · `SummarizeHandler` · `TransitionHandler` · `PurgeHandler` | 작업 종류당 하나. 아래 §작업별 동작 |
| `MinIntervalLimiter` | 외부 API별 최소 호출 간격(국가법령정보·임베딩 각각) |
| `IngestRunStore` | `ingest_runs`: 실행 기록 · 중단된 실행 정리 |

`IngestService.ingestPending()`은 이 잡이 대체하므로 **제거**한다(실행 경로 하나).

## 실행 모델 · 명령 · 종료 코드
- `--spring.profiles.active=batch`(웹 서버 비활성: `spring.main.web-application-type=none`). 같은 컨테이너 이미지.
- 정기: **매일 04:00 KST `run`**, **매주 일요일 `run --full`**(외부 스케줄러).

| 명령 | 동작 |
|---|---|
| `run` | 탐색 + 큐 소진. 초기 백필도 이것(DB가 비면 전부 신규로 분류) |
| `run --full` | 목록의 **모든** 법령에 `SYNC` 등록(MST가 같아도) — 기준선 재검증(§탐색 마지막) |
| `run --dry-run` | 탐색 결과(무엇을 할지)만 `ingest_runs`에 기록. 큐·`law_versions`에 쓰지 않음 |
| `sync <lawId> <efYd> [mst]` | 단건 `SYNC` 등록 후 소진. `mst`를 생략하면 저장된 시행예정본의 값, 그것도 없으면 거부 |
| `index` | 탐색·수집 없이 **쌓여 있는 `READY` `INDEX`만** 소진 — 색인을 꺼 둔 동안 밀린 작업 처리 |
| `summarize [--dry-run]` | 탐색·수집 없이 **모든 시행예정본에 `SUMMARIZE` 등록 후 소진** — 요약이 없거나 낡은 것만 만든다(유효한 요약은 유료 호출 없이 건너뜀). 쉬운 요약 일괄 생성·채우기(유료 Opus, D67). `--dry-run`은 요약이 필요한 시행예정본 수(`planned.SUMMARIZE`)만 기록하고 등록·소진(유료 호출)은 하지 않는다 — 비용 확인용 |
| `reindex-all` | 시행예정 전체에 `INDEX` 등록 후 소진(임베딩 모델 교체 시) |
| `requeue-dead` | `DEAD` 작업의 **기존 행**을 `READY`로(`task_id`·시도 이력 유지, 시도 횟수 0) 되돌린 뒤 소진. 되돌아온 `SUMMARIZE`는 이 명령이 처리하지 않고 `run`(요약 켜짐) 또는 `summarize`를 기다린다. `DEAD` `SUMMARIZE`는 격리 작업이라 `summarize`가 다시 등록하지 않는다 — `requeue-dead`로 먼저 풀어야 한다 |

**설정 `lia.batch.index.enabled`**(기본 `true`, env `LIA_BATCH_INDEX_ENABLED`) — `false`면 **`run`(자동 경로)의 워커가 `INDEX`를 가져가지 않는다**. 수집(`SYNC`)·시행 전환·철회는 그대로 돌고, `INDEX`는 `READY`로 남는다(법령 버전당 하나라 무한히 쌓이지 않음). 실행 기록에 `deferred.INDEX`(남은 `READY` `INDEX` 수)를 남기고, 의도된 미룸이라 결과는 `SUCCESS`. 사람이 직접 친 명령(`index`·`reindex-all`·`sync`)은 이 설정과 무관하게 `INDEX`까지 처리한다 — 명령했다면 그게 의도다.
- 꺼 둔 동안: `TRANSITION`·`PURGE`의 색인 제거는 계속된다(삭제만 — 임베딩 비용 없음) → "시행예정 색인에는 시행예정 버전만" 불변식 유지. 대신 새·바뀐 법령은 검색 색인에 반영되지 않는다(정확 조회는 정본이라 영향 없음). "색인 지연" 신호로 보인다.
- 플래그(`run --no-index`) 대신 설정인 이유: 정기 실행(cron) 줄을 고치지 않고 켜고 끈다.

**설정 `lia.batch.summary.enabled`**(기본 **`false`**, env `LIA_BATCH_SUMMARY_ENABLED`) — `true`일 때만 `run`의 워커가 `SUMMARIZE`를 가져간다. 꺼져 있으면 `SUMMARIZE`는 `READY`로 남고 실행 기록에 `deferred.SUMMARIZE`. **유료(Opus)라 위 색인 설정과 달리 다른 수동 명령(`sync`·`index`·`reindex-all`·`requeue-dead`)도 `SUMMARIZE`를 처리하지 않는다** — `summarize` 명령으로만(D67). 최소 호출 간격 `lia.batch.summary-interval`.

| 종료 코드 | 실행 결과 | 판정 기준 |
|---|---|---|
| 0 | `SUCCESS` | 아래 둘에 해당하지 않음. 이미 다른 실행이 돌고 있어 그냥 종료한 경우도 0(실행 행을 만들지 않음, 로그로 구분) |
| 1 | `FAILED` | 실행 흐름 자체가 예외로 끝남(DB 접속 불가, 예상 못 한 예외). 비정상 종료돼 다음 실행이 정리한 실행도 `FAILED` |
| 2 | `PARTIAL` | 이번 실행에서 실패·격리된 작업이 1건 이상 · 목록 미완주(`run`, `--dry-run` 포함) · 종료 신호로 멈춰 `READY`가 남음 |

- 이전 실행에서 이미 격리돼 있던 `DEAD`는 이번 실행 결과에 넣지 않는다 — 그건 별도 신호(`DEAD ≥ 1`)다.
- 설정으로 미룬 `INDEX`(`deferred.INDEX`)·`SUMMARIZE`(`deferred.SUMMARIZE`)도 결과에 넣지 않는다.

## 데이터 모델 (Flyway `V5__ingest_batch.sql`)
```sql
-- law_versions: status 값 확장(text — 도메인 Law.Status 가 SSOT, 컬럼 CHECK 없음) + 사라짐 판정
--   시행중 · 시행예정 · 과거(대체된 시행중본) · 철회(목록에서 사라진 시행예정본)
ALTER TABLE law_versions ADD COLUMN missing_since date;   -- 완주 목록에서 처음 안 보인 날(KST)

CREATE TABLE ingest_runs (                          -- 실행 단위
  run_id        bigserial PRIMARY KEY,
  command       text        NOT NULL,             -- run · run --full · run --dry-run · sync … · index · summarize · reindex-all · requeue-dead
  started_at    timestamptz NOT NULL,
  finished_at   timestamptz,
  outcome       text        CHECK (outcome IN ('SUCCESS', 'PARTIAL', 'FAILED')),
  list_complete boolean,                          -- 목록을 totalCnt 까지 받았는가(사라짐 판정 전제). 탐색 없는 명령은 NULL
  counts        jsonb       NOT NULL DEFAULT '{}', -- listed · planned.<종류>(계획) · enqueued.<종류>(실제 신규 등록) · done · failed · dead · deferred.INDEX · deferred.SUMMARIZE …
  CHECK ((finished_at IS NULL) = (outcome IS NULL)) -- 종료 기록은 둘 다 있거나 둘 다 없다
);

CREATE TABLE ingest_tasks (                         -- 작업의 현재 상태
  task_id          bigserial   PRIMARY KEY,
  kind             text        NOT NULL CHECK (kind IN ('SYNC', 'INDEX', 'SUMMARIZE', 'TRANSITION', 'PURGE')),   -- SUMMARIZE 는 V9(D67)
  law_id           text        NOT NULL,
  effective_date   date        NOT NULL,
  mst              text,                          -- SYNC 전용(본문 fetch 키)
  status           text        NOT NULL CHECK (status IN ('READY', 'RUNNING', 'DONE', 'DEAD')),
  attempts         int         NOT NULL DEFAULT 0, -- RUNNING 으로 가져간 횟수(중단된 시도 포함)
  last_error       text,
  last_attempt_run bigint      REFERENCES ingest_runs, -- 마지막으로 가져간 실행 = 소유권 토큰
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL,
  CHECK ((kind = 'SYNC') = (mst IS NOT NULL))
);
-- 같은 (종류, 법령 버전)의 미완료·격리 작업은 하나만 — 탐색이 격리 작업을 다시 등록하지 못하게 DEAD 포함
CREATE UNIQUE INDEX ingest_tasks_open_uq ON ingest_tasks (kind, law_id, effective_date)
  WHERE status IN ('READY', 'RUNNING', 'DEAD');
CREATE INDEX ingest_tasks_status ON ingest_tasks (status);

CREATE TABLE ingest_task_attempts (                 -- 시도 이력
  attempt_id    bigserial   PRIMARY KEY,
  task_id       bigint      NOT NULL REFERENCES ingest_tasks ON DELETE CASCADE, -- DONE 보존 정리 시 함께 삭제
  run_id        bigint      NOT NULL REFERENCES ingest_runs,
  attempt       int         NOT NULL,             -- 이 시도를 포함한 ingest_tasks.attempts
  started_at    timestamptz NOT NULL,
  finished_at   timestamptz,
  outcome       text        CHECK (outcome IN ('DONE', 'RETRY', 'DEAD', 'ABANDONED')),
  error_type    text,                             -- PERMANENT · TRANSIENT · ABANDONED + 예외 클래스
  error_message text
);
CREATE INDEX ingest_task_attempts_task ON ingest_task_attempts (task_id);
```
- 등록 = `INSERT … ON CONFLICT (kind, law_id, effective_date) WHERE status IN (…) DO NOTHING` — 중복 등록은 조용히 무시.
- **격리된 작업은 `requeue-dead`로만 풀린다.** 탐색이 매일 같은 실패를 반복 등록하지 않는다.
- **보존:** `DONE`은 30일 뒤 실행 준비 단계에서 삭제(시도 행 함께). `DEAD`는 사람이 처리할 때까지 남긴다. `ingest_runs`는 지우지 않는다.
- **식별 키:** 법령 버전 = `(law_id, effective_date)`(D43 — 시행예정본이 여럿이어도 시행일이 다르다는 실측). 같은 키가 목록에 둘 이상 오면 첫 행만 쓰고 `duplicateKeys` 건수·경고를 남긴다(키 가정이 깨졌다는 신호).

## Business Flow (실행 한 번)
1. **잠금** — `pg_try_advisory_lock(hashtext('lia-ingest-job'))`(세션 잠금 — 잡의 전용 커넥션이 끝까지 보유). 실패하면 "이미 실행 중" 로그 후 종료 코드 0.
2. **정리** — 잠금을 잡았으므로 아래는 모두 죽은 실행의 흔적이다.
   - `finished_at IS NULL`인 실행 → `FAILED`로 닫음(`counts.abandoned = 1`).
   - 끝나지 않은 시도 행 → `ABANDONED`. `RUNNING` 작업 → `READY`(시도 횟수가 한도면 `DEAD` — 프로세스를 죽이는 작업이 무한 반복되지 않게).
   - 30일 지난 `DONE` 삭제.
3. **시작** — `ingest_runs` 행 생성.
4. **등록** — `Discoverer`(명령에 따라 생략·대체: `sync`·`reindex-all`·`summarize`·`requeue-dead`는 해당 작업만 등록, `index`는 등록 없음). `--dry-run`은 여기서 기록 후 종료.
5. **소진** — 워커 풀이 작업을 처리한다. 실행 중에 생긴 작업(SYNC → INDEX, TRANSITION → SYNC)도 같은 실행에서 처리한다. 워커는 가져갈 작업이 없어도 **진행 중인 작업이 남아 있으면** 잠시 기다렸다 다시 가져가고(그 작업이 새 작업을 만들 수 있으므로), **가져갈 작업도 진행 중인 작업도 없을 때** 종료한다.
6. **기록** — 건수·결과·`list_complete`·종료 시각을 `ingest_runs`에. 예외로 끝나도 `FAILED`로 기록한 뒤 전파한다. 잠금 해제.

## 탐색 분류 규칙
**목록에 있는 법령**(`lawId`, 시행일, `MST`)

| 저장 상태 | 분류 | 등록 |
|---|---|---|
| 같은 `(lawId, 시행일)`의 시행예정 행 없음(철회된 행 포함) | 신규 | `SYNC` |
| 행 있음, 저장된 `mst`와 다름 | 변경 의심(정정·재공포) | `SYNC` |
| 행 있음, `mst` 같음 | 동일 | 없음 (`missing_since` 있으면 지움) |

`MST`(법령일련번호)는 버전마다 달라 **같으면 본문 fetch를 생략**한다(싼 증분 키). 본문을 받은 뒤에는 `revision`(D16)과 상태가 같으면 저장·색인을 생략한다(비싼 증분 키, [[IngestService]]).

목록에 **보였다는 것은 확실한 증거**이므로 목록이 미완주여도 보인 행의 `missing_since`는 지운다. 반대로 "안 보였다"는 목록을 끝까지 받았을 때만 증거가 된다.

**잘못된 목록 행** — `lawId`·시행일·`MST` 중 하나라도 없는 행은 건너뛰고 `malformedRows`로 세며 경고를 남긴다. 그런 행이 하나라도 있으면 그 실행의 목록은 **미완주로 취급**한다(그 법령이 "안 보인 것"인지 알 수 없으므로 사라짐 판정 안 함, `list_complete=false` → `PARTIAL`). 행 하나 때문에 탐색 전체(시행 전환 포함)가 실패하지 않는다.

**저장돼 있지만 목록에 없는 시행예정본** (위에서부터 처음 맞는 규칙)

| 조건 | 처리 |
|---|---|
| 시행일 ≤ 오늘 | `TRANSITION` 등록 — 날짜 기준이라 목록 완주와 무관 |
| 시행일 > 조회 상한(오늘 + `lookahead-years`) | 판정 안 함 — 조회 범위 밖이라 목록에 없는 게 정상 |
| 목록 실패·미완주 | 판정 안 함 — `missing_since` 기록·`PURGE` 모두 없음 |
| `missing_since` 없음 | `missing_since = 오늘` (첫 미확인 — 정리 안 함) |
| `missing_since = 오늘` | 아무것도 안 함 — 같은 날 다시 돌린 실행은 새 증거가 아니다 |
| `missing_since < 오늘` | `PURGE` 등록 (다른 날의 두 번째 미확인) |

- **"두 번 연속 미확인" = 서로 다른 날짜(KST)의 완주 실행 두 번**, 그 사이 한 번도 보이지 않음. 미완주·실패 실행은 세지도 초기화하지도 않는다. 별도 카운터·실행 ID 컬럼은 두지 않는다(날짜가 이미 그 정보다). 정상 일정에서 철회는 첫 미확인 다음 날 등록된다.
- 시행일 변경(같은 법령이 다른 시행일로 다시 등장)은 새 행이 신규로 들어오고 옛 행은 사라짐 경로로 `철회`된다.
- `--dry-run`은 같은 분류로 계획 건수만 세고 `missing_since`도 쓰지 않는다.

**주 1회 전체 재검증(`run --full`)** — 공포 즉시 시행되는 개정은 시행예정 목록에 나타나지 않아, 매일 실행만으로는 우리 기준선(시행중본)이 낡을 수 있다(같은 법령의 다른 시행예정본 diff도 틀어짐). 주 1회 목록 전체에 `SYNC`를 등록해 기준선을 다시 받는다. revision 비교로 실제 바뀐 것만 재색인된다(국가법령정보 호출 ≈ 목록 수 × 2).

## 작업별 동작
| 작업 | 동작 | 완료(`DONE`) 조건 |
|---|---|---|
| `SYNC(lawId, 시행일, mst)` | 시행일이 오늘 이전이거나 저장된 행이 `시행중`·`과거`면 **아무것도 안 함**(그 버전은 `TRANSITION`이 맡는다 — 오래된 SYNC가 전환된 버전을 시행예정으로 되돌리지 않게). 그 외: `fetchPending`·`fetchCurrent`(트랜잭션 밖) → **한 트랜잭션**: [[IngestService]] `store`(revision·상태가 같으면 저장 생략) · 같은 법령의 더 오래된 `시행중` → `과거` · `missing_since` 지움 · 시행예정본이 바뀌었으면 `INDEX`·`SUMMARIZE` 등록 | 원문 조회·정본 저장(또는 동일해 생략)·필요 시 `INDEX`·`SUMMARIZE` 등록까지 커밋. **색인 완료를 뜻하지 않는다** |
| `INDEX(lawId, 시행일)` | [[LawStore]]에서 정본을 읽어 [[RAGIndexer]] `index` → [[ChunkStore]] `replaceVersion`(**한 트랜잭션**). 상태가 `시행예정`이 아니면(그 사이 전환·철회) 아무것도 안 함 | 벡터 교체 커밋, 또는 할 일 없음 |
| `SUMMARIZE(lawId, 시행일)` | [[LawStore]]에서 정본을 읽어 `AnalysisEngine` SUMMARY(조립→Opus→인용 검증→재생성≤N) → `law_summaries` upsert(정본 revision·프롬프트 버전·모델과 함께). 상태가 `시행예정`이 아니면 아무것도 안 함. 인용 검증이 끝내 실패하면 **저장하지 않고 완료**(재시도해도 같은 입력이라 같은 실패 — 화면은 실시간 대체) | 요약 저장 커밋, 또는 할 일 없음 |
| `TRANSITION(lawId, 시행일)` | **한 트랜잭션**: 상태 `시행예정 → 시행중`, 같은 법령의 더 오래된 `시행중` → `과거`, 이 버전의 pending 색인 제거(`replaceVersion(…, [])`), 같은 법령의 **남은 시행예정본마다 `SYNC`** 등록(기준선이 바뀌어 diff 재생성 필요). 버전이 이미 `시행예정`이 아니면 상태는 건드리지 않고 **그 버전의 시행예정 색인만 제거**(시행예정이 아닌 버전의 시행예정 청크 제거는 언제나 옳다) | 상태 전환·색인 제거·형제 `SYNC` 등록, 또는 색인 제거만 |
| `PURGE(lawId, 시행일)` | **한 트랜잭션**: 상태 → `철회`, 이 버전의 색인 제거. **행은 남긴다**. 버전이 `시행예정`이 아니면 색인만 제거, `missing_since`가 없으면(그 사이 목록에 다시 보임) 아무것도 안 함 | 상태 전환·색인 제거, 또는 색인 제거만·할 일 없음 |

**멱등성** — 모든 작업은 재실행해도 결과가 같다.
- `SYNC`: 저장과 `INDEX` 등록이 한 트랜잭션이라, 저장만 되고 색인 등록이 빠지는 상태(재시도 시 revision이 같아 영영 색인 안 됨)가 생기지 않는다.
- `INDEX`·`PURGE`: 정본 단위 완전 교체·상태 덮어쓰기.
- `TRANSITION`·`PURGE`: 상태 변경과 색인 제거를 **한 트랜잭션**으로 — 벡터 저장소도 같은 DB라 `replaceVersion`의 트랜잭션이 바깥 트랜잭션에 참여한다. 색인 제거가 실패하면 상태도 롤백돼, "상태는 시행중·철회인데 시행예정 청크가 검색에 남는" 상태가 생기지 않는다. 시작 시 **`시행예정`일 때만 상태를 바꾸는 가드** — 오래된 `PURGE`가 이미 시행된 버전을 철회하거나, 오래된 `TRANSITION`이 철회된 버전을 되살리지 않는다. 가드에 걸려도 그 버전의 시행예정 색인은 제거한다 — 다른 경로(형제 `SYNC`의 기준선 저장)로 상태만 먼저 바뀐 경우에도 "시행예정 색인에는 시행예정 버전만"이 지켜진다. `PURGE`는 `missing_since`가 지워졌으면(다시 보임) 철회하지 않는다.
- `SYNC`: 시행일이 지났거나 이미 `시행중`·`과거`인 버전은 건드리지 않는다 — 실패로 남은 오래된 `SYNC`·`requeue-dead`·수동 `sync`가 전환된 버전을 시행예정으로 되돌리고 다시 색인하지 않게.
- **철회본 재등장**: 철회된 버전이 목록에 다시 보이면 신규로 분류돼 `SYNC`되고, 내용이 같아도 상태가 다르므로 `시행예정`으로 복원·재색인된다.
- **행 보존 vs 색인 삭제**: `과거`·`철회`는 `law_versions` 행을 남기고(인용 추적) `vector_store`의 그 버전 청크만 지운다.

## 상태 전이
**작업(`ingest_tasks.status`)**

| 전이 | 계기 | 함께 일어나는 일 |
|---|---|---|
| (없음) → `READY` | 등록 | 같은 열린 작업이 있으면 무시 |
| `READY → RUNNING` | 가져가기 | `attempts + 1`, `last_attempt_run` = 이번 실행, 시도 행 INSERT — 한 문장 |
| `RUNNING → DONE` | 성공 | 시도 행 `DONE` |
| `RUNNING → READY` | 일시적 오류, `attempts < 3` | 시도 행 `RETRY`, `last_error` |
| `RUNNING → DEAD` | 영구 오류, 또는 `attempts ≥ 3` | 시도 행 `DEAD`, `last_error` |
| `RUNNING → READY` / `DEAD` | 다음 실행의 정리 단계(중단 복구) | 시도 행 `ABANDONED`, `attempts` 유지(한도면 `DEAD`) |
| `DEAD → READY` | `requeue-dead` | 같은 `task_id`, `attempts = 0`, `last_attempt_run = NULL`, 시도 이력 보존 |
| `DONE →` 삭제 | 30일 경과 | 시도 행 함께 삭제 |

**법령 버전(`law_versions.status`, SSOT `Law.Status`)**

| 전이 | 계기 |
|---|---|
| (없음) → `시행예정` | `SYNC` |
| `시행예정 → 시행중` | `TRANSITION`(시행일 도래) |
| `시행중 → 과거` | `SYNC`·`TRANSITION` — 같은 법령에 더 새 시행중본이 생김 |
| `시행예정 → 철회` | `PURGE`(다른 날 두 번 미확인) |
| `철회 → 시행예정` | `SYNC`(목록에 다시 등장) |

## 동시성 · 트랜잭션 · 호출 한도
- **공용 워커 풀** — 크기는 설정값(`lia.batch.workers`, 기본 2). 가져가기 우선순위: `TRANSITION`·`PURGE`(DB만) → `SYNC` → `INDEX`.
- **가져가기 = 한 문장(원자적)** — 선점·시도 횟수 증가·소유권 토큰·시도 행 기록을 데이터 변경 CTE 하나로:
  ```sql
  WITH claimed AS (
    UPDATE ingest_tasks
    SET status = 'RUNNING', attempts = attempts + 1, last_attempt_run = :runId, updated_at = now()
    WHERE task_id = (SELECT task_id FROM ingest_tasks
                     WHERE status = 'READY' AND last_attempt_run IS DISTINCT FROM :runId
                       AND law_id <> ALL(:busyLawIds)          -- 지금 처리 중인 법령 제외
                       AND kind = ANY(:kinds)                  -- 이번 실행이 처리하는 종류(run + 색인 꺼짐 → INDEX 제외, index → INDEX 만)
                     ORDER BY CASE kind WHEN 'TRANSITION' THEN 0 WHEN 'PURGE' THEN 0
                                        WHEN 'SYNC' THEN 1 ELSE 2 END, task_id
                     LIMIT 1 FOR UPDATE SKIP LOCKED)
    RETURNING task_id, kind, law_id, effective_date, mst, attempts),
  attempt AS (
    INSERT INTO ingest_task_attempts (task_id, run_id, attempt, started_at)
    SELECT task_id, :runId, attempts, now() FROM claimed
    RETURNING attempt_id, task_id)
  SELECT c.*, a.attempt_id FROM claimed c JOIN attempt a USING (task_id);
  ```
- **완료·실패 = 소유권 확인** — `UPDATE ingest_tasks … WHERE task_id = :id AND status = 'RUNNING' AND last_attempt_run = :runId`와 시도 행 갱신을 한 트랜잭션으로. **0행이면 소유권을 잃은 것**(예: 네트워크 단절로 잠금 커넥션이 끊겨 새 실행이 시작·복구함)이라 롤백하고 결과를 큐에 반영하지 않는다. 한계: 옛 작업자가 그 전에 쓴 `law_versions`·벡터는 되돌리지 않는다 — revision 비교 upsert·정본 단위 교체라 같은 결과로 수렴한다.
- **lease를 두지 않는 이유** — lease는 여러 프로세스가 동시에 큐를 소비할 때 "죽었는지 모르는 작업자"를 회수하는 장치다. 여기서는 잠금이 단일 프로세스를 보장하므로 잠금 획득 = 이전 작업자 전원 사망이고, 같은 프로세스 안의 무한 대기는 **호출 타임아웃**이 막는다(국가법령정보 20초 — `LawConnector`, 임베딩은 Spring AI 클라이언트). 프로세스는 살아 있는데 멈춘 경우는 잠금을 계속 쥐어 다음 실행이 0으로 종료되므로 "마지막 성공 실행 26시간 초과" 신호로 잡는다.
- **같은 법령의 작업은 동시에 돌지 않는다** — 시행예정본이 여럿인 법령(D43)은 TRANSITION과 형제 SYNC가 같은 기준선 행을 건드린다. 프로세스 안에서 처리 중인 법령 집합(`busyLawIds`)을 들고, 가져가기 쿼리가 그 법령을 **처음부터 제외**한다. 두 워커가 같은 법령을 동시에 가져가지 않도록 "가져가기 + 집합 등록"은 프로세스 안에서 직렬로 한다(가져가기는 짧은 쿼리라 병목이 아니다).
  - 가져간 뒤 돌려놓는 방식은 쓰지 않는다 — 가져가는 순간 `last_attempt_run`이 찍혀 그 작업이 이번 실행에서 다시 잡히지 않기 때문이다.
- **트랜잭션 경계** — 외부 API 호출(fetch)은 어떤 DB 트랜잭션에도 들어가지 않는다. 트랜잭션은 ① 가져가기(한 문장) ② `SYNC`의 저장+등록(DB만) ③ 완료·실패 기록 ④ `replaceVersion`. ④는 예외로 **임베딩 호출이 트랜잭션 안**이다(PgVectorStore `add`가 내부에서 임베딩) — 법령 1건 수 초, MVCC라 읽기는 막지 않는다. 길어지면 임베딩을 먼저 계산하고 삽입만 트랜잭션으로 옮긴다.
- **API별 최소 호출 간격**(`MinIntervalLimiter`) — 국가법령정보·임베딩 각각 설정값. 워커 수와 무관하게 API로 나가는 속도를 제한한다. 국가법령정보의 공식 호출 한도는 확인 후 기본값에 반영(현재는 보수적으로).

## Error Handling
**재시도는 두 층이다.**

| 층 | 대상 | 방식 |
|---|---|---|
| 요청 단위 | 국가법령정보 네트워크·5xx·429 | `LawConnector` 지수 백오프(0.5초·1초, 최대 3회 시도). 429는 `Retry-After`(초)를 따름(30초 상한) |
| 요청 단위 | 임베딩 | Spring AI 재시도. 기본값(10회·2초×5배)은 워커를 수 분 묶으므로 배치 프로파일에서 `spring.ai.retry.max-attempts=3`·짧은 백오프로 줄인다 |
| 작업 단위 | 요청 재시도 후에도 실패한 작업 | 다음 실행(하루 뒤)에 재시도. 실행당 1회 |

| 분류 | 해당 | 처리 |
|---|---|---|
| 일시적 | 네트워크·타임아웃, 요청 재시도 후 남은 5xx, 408·429, DB 연결 오류 | `READY`로 돌려 다음 실행에서 재시도. `attempts ≥ 3`이면 `DEAD` |
| 영구 | API 논리 오류(`LawApiException`), 408·429 외 4xx, 정규화·파싱 실패, 제약 위반(코드 버그) | 즉시 `DEAD` |
| 모름 | 그 밖의 예외 | 일시적으로 취급(3회면 격리) |

- **`attempts` 정의** — 작업을 `RUNNING`으로 가져간 횟수. 끝까지 못 간(중단된) 시도도 센다 — JVM을 죽이는 작업도 3회면 격리된다.
- **실행당 1회** — 가져가기가 `last_attempt_run = 이번 실행`인 작업을 제외한다.
- **목록 조회 전체 실패** — DB만으로 판정하는 `TRANSITION`은 등록·처리하고, 신규·변경·사라짐 판정은 건너뛴다(`list_complete=false`, `PARTIAL`).
- **색인 실패** — `replaceVersion`이 한 트랜잭션이라 롤백되면 **이전 청크가 그대로 남는다**(검색이 비지 않음).
- **종료 신호(SIGTERM)** — 새 작업을 가져가지 않고 **진행 중 작업이 끝나고 실행 기록이 남을 때까지 기다린 뒤** 종료한다(종료 훅이 최대 대기 시간 동안 실행 종료를 기다리고, 그동안 DB 연결은 열려 있다). compose `ingest`는 `stop_grace_period: 60s` — 작업 하나가 HTTP 타임아웃(20초)·재시도로 기본 10초를 넘을 수 있어서. `READY`가 남으면 실행 결과는 `PARTIAL`이지만 **프로세스 종료 코드는 신호 종료(143)** 다 — 결과는 `ingest_runs`가 기준. 대기 시간을 넘기면 강제 종료와 같다(다음 실행이 정리).
- **워커 종료 대기** — 소진이 끝나거나 워커 하나가 예외로 죽으면 나머지 워커를 중단시키고 **끝날 때까지 기다린 뒤**(제한 시간) 실행을 기록·잠금을 해제한다 — 실행 기록·잠금 해제 뒤에 워커가 살아 남아 단일 실행 불변식을 깨지 않게.
- **강제 종료(SIGKILL·OOM)** — 실행 행이 열린 채 남는다. "종료된 실행은 반드시 `finished_at`·`outcome`을 가진다"는 **다음 실행의 정리 단계가 보장**한다(즉시 보장은 불가능).

## Observability
**한 번 돌고 끝나는 잡이라 Prometheus가 긁기 전에 프로세스가 사라진다.** 그래서 기준 출처를 바꾼다.

| 수단 | 용도 |
|---|---|
| **`ingest_runs` (기준 출처)** | 실행별 결과·건수·소요 시간. Grafana가 Postgres 데이터 소스로 직접 조회 |
| **`ingest_task_attempts`** | 작업별 시도마다 실행·시각·결과·오류 — 격리 작업이 *왜* 실패했는지(로그는 컨테이너와 함께 사라짐) |
| 트레이스(OTLP push → Tempo) | 실행 span 아래 작업 span(`kind` 태그), 그 아래 기존 `lia.ingest`·`lia.normalize`·`lia.diff` |
| 구조화 로그 | 작업마다 한 줄: 종류·법령·결과·소요 시간·오류 |

Pushgateway는 두지 않는다. **신호:** `DEAD ≥ 1` · 마지막 성공 실행이 26시간 넘게 없음 · 목록 미완주 연속 · `abandoned` 실행 · `duplicateKeys > 0` · 가장 오래된 `READY`의 나이 · 색인 지연(`READY` `INDEX` 수 = `deferred.INDEX`, 색인을 꺼 두면 늘어남).

## Invariants
- 한 시점에 배치는 **하나만** 돈다(advisory lock).
- 같은 `(종류, 법령 버전)`의 미완료·격리 작업은 **하나만** 존재한다(부분 유니크 인덱스).
- `SYNC` 작업만 `mst`를 가진다(CHECK).
- 같은 법령의 작업은 **동시에 처리되지 않는다.**
- 작업 상태 변경(완료·실패)은 **그 작업을 가져간 실행만** 할 수 있다(`last_attempt_run`).
- **두 번 연속 실행해도 결과가 같다** — 두 번째 실행은 등록·쓰기가 없다(MST·revision 증분).
- 시행예정 색인에는 `status = 시행예정`인 버전만 있다(전환·철회 시 제거).
- 사라짐 판정은 조회 범위 안의 버전에 대해, **서로 다른 날짜의 완주 목록 두 번**에서 연속 미확인일 때만 한다.
- 다음 실행이 시작된 뒤에는 이전 실행 행이 모두 `finished_at`·`outcome`을 가진다.

## Side Effects
- **DB 쓰기**(`law_versions`·`ingest_tasks`·`ingest_task_attempts`·`ingest_runs`·`vector_store`) · **외부 API 호출**(국가법령정보, 임베딩) · 로그·트레이스.

## Design Constraints
- **오프라인 전용**(D40) — 온라인 요청 경로에서 호출하지 않는다.
- **Layer A 선계산(#38) 연결점** — 쉬운 요약은 `SUMMARIZE`로 붙었다(D67). DIFF·LawFacts 도 같은 방식(작업 종류 + `SYNC` 등록 + `kind` CHECK 마이그레이션).
- **Spring Batch 미사용** — 하루 1회·수백 건 규모에 메타 테이블과 개념이 과하다.
- **임베딩 Batch API 미사용** — 임베딩은 전체 재색인도 저비용으로 추정되고, revision 증분이면 바뀐 법령만 다시 임베딩한다.
- **여러 프로세스로 늘리면** — 잠금을 풀고 큐만으로 분산하려면 그때 lease(`lease_until`)·하트비트·DB 기반 법령 직렬화가 필요하다. 지금은 단일 프로세스 전제.
- 규모가 커지면: 메타데이터 필터 삭제의 전체 스캔(현 `replaceVersion`) → 메타데이터 인덱스 또는 버전 테이블 검토.

## 검증
| 대상 | 방법 | 확인할 것 |
|---|---|---|
| `Discoverer` | 표 기반, 가짜 원천 + Testcontainers | 신규·MST 변경·동일·시행 도래·첫 미확인·같은 날 재실행(PURGE 안 함)·다른 날 두 번째 미확인·조회 범위 밖·목록 미완주·미완주 목록에 보이면 해제·`--full`·`--dry-run`·철회본 재등장·중복 키 |
| `TaskQueue` | Testcontainers | 중복 등록 무시, `DEAD`가 재등록 차단, `SKIP LOCKED` 동시 가져가기, 가져가기 시 시도 행, 소유권 잃은 완료 무시, 중단 복구(`ABANDONED`·한도면 `DEAD`), 이번 실행 시도 작업 제외, 우선순위, 종류 필터, `requeue-dead` 이력 보존, `mst` CHECK |
| `IngestRunStore` | Testcontainers | 종료 기록, 열린 실행 정리, `finished_at`/`outcome` CHECK |
| 핸들러 | 가짜 원천(샘플 fixture) + Testcontainers | SYNC: revision 같으면 저장·INDEX 없음, 저장 후 등록 실패 시 함께 롤백. TRANSITION: 형제 SYNC·`과거` 전환·색인 제거. INDEX: 실패 시 이전 청크 유지. PURGE: `철회`·색인 제거 |
| 잡 전체 | Testcontainers + 가짜 원천 + 가짜 임베딩 | 두 번 실행 = 같은 결과. 실패 주입: 한 법령만 일시 오류 → 나머지 성공, 3회 후 `DEAD`, 영구 오류 즉시 `DEAD`. 잠금 경합 시 두 번째 실행 즉시 종료. 결과 판정(SUCCESS·PARTIAL·FAILED, dry-run 미완주 = PARTIAL) |
| 실제 API | 옵트인 스모크(키·플래그 있을 때만) | `run --dry-run`으로 실제 목록 분류만 확인 |

구현 테스트: `TaskQueueIntegrationTest`(큐·시도 이력·소유권·중단 복구·실행 기록)·`DiscovererIntegrationTest`·`TaskHandlersIntegrationTest`·`IngestJobIntegrationTest`·`JobLockIntegrationTest`·`BatchContextTest`·`ErrorClassifierTest`·`MinIntervalLimiterTest`·`JobCommandTest`·`LawConnectorBatchSupportTest`·`IngestServiceIntegrationTest`(증분·철회본 복원).

## 의존 / 관련
[[IngestService]] · [[RAGIndexer]] · [[LawStore]] · [[ChunkStore]] · [[LawConnector]] · [[concurrency-and-reliability]] §2 · [[deployment]] · D16·D40·D43·D54·D55·D62 · #38·#40
