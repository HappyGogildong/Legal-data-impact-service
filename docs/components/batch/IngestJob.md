---
title: IngestJob — 적재·RAG 색인 배치 잡 (작업 큐)
status: Draft
version: 0.1
date: 2026-10-13
tags: [component, batch, pipeline, ingest, queue, rag]
related: ["components/ingest/IngestService.md", "components/index/RAGIndexer.md", "components/store/LawStore.md", "components/store/ChunkStore.md", "components/connector/LawConnector.md", "backend/concurrency-and-reliability.md", "backend/deployment.md", "adr/decision-log.md"]
---

# IngestJob (적재·RAG 색인 배치 잡)

> 시행예정 법령을 주기적으로 수집해 **정본(`law_versions`)과 RAG 색인(`vector_store`)을 최신으로 유지**하는 오프라인 배치(D40·D62). 같은 이미지를 배치 프로파일로 띄워 **한 번 돌고 종료**하는 잡이고, 일은 **DB 작업 큐(`ingest_tasks`)**로 나눠 워커가 처리한다. 관련: [[IngestService]](조립) · [[RAGIndexer]](청킹) · [[LawStore]] · [[ChunkStore]] · [[concurrency-and-reliability]] §2.

## 설계 결정 (D62)
- **실행 모델 = 1회성 잡 프로파일** — 웹 서버 없이 실행 후 종료. 정기 실행은 외부 스케줄러(로컬: 호스트 cron + `docker compose run`, 클라우드: ECS 예약 작업 등), 수동 실행은 **명령줄 인자**. 온라인과 자원이 분리되고, 수동 실행용 관리자 API·인증이 필요 없다.
- **작업 큐 테이블** — 작업 종류 4개(`SYNC`·`INDEX`·`TRANSITION`·`PURGE`)를 큐에 넣고 공용 워커 풀이 `FOR UPDATE SKIP LOCKED`로 가져간다. 단계별 재시도·재개·병렬 처리를 큐가 책임진다.
- **지나간 버전은 상태로 보존** — `과거`(대체된 시행중본)·`철회`(목록에서 사라진 시행예정본). 행은 남기고 RAG 색인에서만 뺀다(과거 답변의 인용 조문을 계속 되짚을 수 있게).
- **재시도 = 다음 실행에서**, 실행당 1회 시도. 일시적 오류는 3회 연속 실패 시 격리(`DEAD`), 영구 오류는 즉시 격리.
- **Layer A 선계산은 범위 밖**(#38) — 큐에 작업 종류를 추가하는 방식으로 나중에 붙인다(아래 Design Constraints).

## Responsibility
- **담당:** 실행 진입·단일 실행 보장 · 목록 ⟷ 저장 상태 비교(탐색) · 작업 등록·분배·재시도·격리 · 법령 라이프사이클(시행 전환·철회) · 실행 기록.
- **담당 안 함:** 출처 호출 형식([[LawConnector]]) · 정규화·diff·정본 조립([[IngestService]] `store`) · 청킹([[RAGIndexer]]) · 임베딩·벡터 저장([[ChunkStore]]) · Layer A 선계산(#38).

## Collaborators
- 재사용: [[LawConnector]](`listPending`·`fetchPending`·`fetchCurrent`) · [[IngestService]](`store`) · [[RAGIndexer]](`index`) · [[LawStore]] · [[ChunkStore]].
- 외부 시스템: 국가법령정보 API · 임베딩 API(OpenAI, ChunkStore 경유) · Postgres.
- 트리거: 외부 스케줄러(cron 등) — [[deployment]].

## 구조 (컴포넌트) — `com.lia.core.pipeline.batch`
| 컴포넌트 | 책임 |
|---|---|
| `IngestJobRunner` | `batch` 프로파일 진입점(`CommandLineRunner`). 명령 해석 → 잠금 → 잡 실행 → 종료 코드 |
| `IngestJob` | 실행 한 번의 흐름: 준비(복구·보존 정리) → 탐색 → 큐 소진 → 기록 |
| `Discoverer` | 목록 전체 조회 ⟷ `law_versions` 비교 → 작업 등록. 목록 완주 여부 판정 |
| `TaskQueue` | `ingest_tasks`: 등록(중복 방지) · 가져가기(`SKIP LOCKED`) · 완료 · 실패(일시/영구) · 격리 재투입 · `RUNNING` 복구 |
| `SyncHandler` · `IndexHandler` · `TransitionHandler` · `PurgeHandler` | 작업 종류당 하나. 아래 §작업별 동작 |
| `ApiRateLimiter` | 외부 API별 최소 호출 간격(국가법령정보·임베딩 각각) |
| `IngestRunStore` | `ingest_runs`: 실행 기록 |

`IngestService.ingestPending()`은 이 잡이 대체하므로 **제거**한다(실행 경로 하나).

## 실행 모델 · 명령 · 종료 코드
- `--spring.profiles.active=batch`(웹 서버 비활성: `spring.main.web-application-type=none`). 같은 컨테이너 이미지.
- 정기: **매일 04:00 KST `run`**, **매주 일요일 `run --full`**(외부 스케줄러).

| 명령 | 동작 |
|---|---|
| `run` | 탐색 + 큐 소진. 초기 백필도 이것(DB가 비면 전부 신규로 분류) |
| `run --full` | 목록의 **모든** 법령에 `SYNC` 등록(MST가 같아도) — 기준선 재검증(§탐색 마지막) |
| `run --dry-run` | 탐색 결과(무엇을 할지)만 `ingest_runs`에 기록, 큐에 넣지 않음 |
| `sync <lawId> <efYd>` | 단건 `SYNC` 등록 후 소진 |
| `reindex-all` | 시행예정 전체에 `INDEX` 등록 후 소진(임베딩 모델 교체 시) |
| `requeue-dead` | `DEAD` 작업을 `READY`로(시도 횟수 0) 되돌린 뒤 소진 |

| 종료 코드 | 뜻 |
|---|---|
| 0 | 성공(실패 작업 없음). 이미 다른 실행이 돌고 있어 그냥 종료한 경우도 0(로그로 구분) |
| 1 | 실행 자체 실패(예상 못 한 예외, DB 접속 불가 등) |
| 2 | 부분 실패(일부 작업 실패·격리, 또는 목록 미완주) |

## 데이터 모델 (Flyway `V5__ingest_batch.sql`)
```sql
-- law_versions: status 값 확장(text — 도메인 Law.Status 가 SSOT) + 사라짐 판정
--   시행중 · 시행예정 · 과거(대체된 시행중본) · 철회(목록에서 사라진 시행예정본)
ALTER TABLE law_versions ADD COLUMN missing_since date;   -- 목록에서 처음 안 보인 날

CREATE TABLE ingest_runs (
  run_id        bigserial PRIMARY KEY,
  command       text        NOT NULL,             -- run · run --full · run --dry-run · sync · reindex-all · requeue-dead
  started_at    timestamptz NOT NULL,
  finished_at   timestamptz,
  outcome       text,                             -- SUCCESS · PARTIAL · FAILED
  list_complete boolean,                          -- 목록을 totalCnt 까지 받았는가(사라짐 판정 전제)
  counts        jsonb       NOT NULL DEFAULT '{}' -- listed · enqueued(종류별) · done · failed · dead · skipped
);

CREATE TABLE ingest_tasks (
  task_id          bigserial   PRIMARY KEY,
  kind             text        NOT NULL,          -- SYNC · INDEX · TRANSITION · PURGE
  law_id           text        NOT NULL,
  effective_date   date        NOT NULL,
  mst              text,                          -- SYNC 전용(본문 fetch 키)
  status           text        NOT NULL,          -- READY · RUNNING · DONE · DEAD
  attempts         int         NOT NULL DEFAULT 0,
  last_error       text,
  last_attempt_run bigint      REFERENCES ingest_runs,
  created_at       timestamptz NOT NULL,
  updated_at       timestamptz NOT NULL
);
-- 같은 (종류, 법령 버전)의 미완료·격리 작업은 하나만 — 탐색이 격리 작업을 다시 등록하지 못하게 DEAD 포함
CREATE UNIQUE INDEX ingest_tasks_open_uq ON ingest_tasks (kind, law_id, effective_date)
  WHERE status IN ('READY', 'RUNNING', 'DEAD');
CREATE INDEX ingest_tasks_status ON ingest_tasks (status);
```
- 등록 = `INSERT … ON CONFLICT (kind, law_id, effective_date) WHERE status IN (…) DO NOTHING` — 중복 등록은 조용히 무시.
- **격리된 작업은 `requeue-dead`로만 풀린다.** 탐색이 매일 같은 실패를 반복 등록하지 않는다.
- **보존:** `DONE`은 30일 뒤 실행 준비 단계에서 삭제. `DEAD`는 사람이 처리할 때까지 남긴다.

## Business Flow (실행 한 번)
1. **잠금** — `pg_try_advisory_lock(hashtext('lia-ingest-job'))`(세션 잠금 — 잡의 전용 커넥션이 끝까지 보유). 실패하면 "이미 실행 중" 로그 후 종료 코드 0.
2. **준비** — `ingest_runs` 행 생성. 직전 실행이 죽어 남은 `RUNNING` → `READY` 복구. 30일 지난 `DONE` 삭제.
3. **탐색** — `Discoverer`(명령에 따라 생략·대체: `sync`·`reindex-all`·`requeue-dead`는 해당 작업만 등록).
4. **소진** — 워커 풀이 작업을 처리한다. 실행 중에 생긴 작업(SYNC → INDEX, TRANSITION → SYNC)도 같은 실행에서 처리한다. 워커는 가져갈 작업이 없어도 **진행 중인 작업이 남아 있으면** 잠시 기다렸다 다시 가져가고(그 작업이 새 작업을 만들 수 있으므로), **가져갈 작업도 진행 중인 작업도 없을 때** 종료한다.
5. **기록** — 건수·결과·`list_complete`·종료 시각을 `ingest_runs`에. 잠금 해제.

## 탐색 분류 규칙
**목록에 있는 법령**(`lawId`, 시행일, `MST`)

| 저장 상태 | 분류 | 등록 |
|---|---|---|
| 같은 `(lawId, 시행일)` 행 없음 | 신규 | `SYNC` |
| 행 있음, 저장된 `mst`와 다름 | 변경 의심(정정·재공포) | `SYNC` |
| 행 있음, `mst` 같음 | 동일 | 없음 (`missing_since` 있으면 지움) |

`MST`(법령일련번호)는 버전마다 달라 **같으면 본문 fetch를 생략**한다(싼 증분 키). 본문을 받은 뒤에는 `revision`(D16)이 같으면 저장·색인을 생략한다(비싼 증분 키, `SyncHandler`).

**저장돼 있지만 목록에 없는 시행예정본**

| 조건 | 처리 |
|---|---|
| 시행일 ≤ 오늘 | `TRANSITION` 등록 — 날짜 기준이라 목록 완주와 무관 |
| 시행일 > 오늘, 목록 완주, `missing_since` 없음 | `missing_since = 오늘` (첫 미확인 — 정리 안 함) |
| 시행일 > 오늘, 목록 완주, `missing_since` 있음 | `PURGE` 등록 (두 번째 미확인) |
| 목록 미완주 | 사라짐 판정을 하지 않음 — 일부만 받은 목록으로 멀쩡한 법령을 지우지 않게 |

시행일 변경(같은 법령이 다른 시행일로 다시 등장)은 새 행이 신규로 들어오고 옛 행은 사라짐 경로로 `철회`된다.

**주 1회 전체 재검증(`run --full`)** — 공포 즉시 시행되는 개정은 시행예정 목록에 나타나지 않아, 매일 실행만으로는 우리 기준선(시행중본)이 낡을 수 있다(같은 법령의 다른 시행예정본 diff도 틀어짐). 주 1회 목록 전체에 `SYNC`를 등록해 기준선을 다시 받는다. revision 비교로 실제 바뀐 것만 재색인된다(국가법령정보 호출 ≈ 목록 수 × 2).

## 작업별 동작
| 작업 | 동작 | 성공 시 |
|---|---|---|
| `SYNC(lawId, 시행일, mst)` | `fetchPending`·`fetchCurrent` → [[IngestService]] `store`(정규화·diff·저장, **revision이 같으면 저장 생략**). 기준선 upsert 후 같은 법령의 더 오래된 `시행중` 행 → `과거`. `missing_since` 지움 | 시행예정본 revision이 바뀌었으면 `INDEX` 등록 |
| `INDEX(lawId, 시행일)` | [[LawStore]]에서 정본을 읽어 [[RAGIndexer]] `index` → [[ChunkStore]] `replaceVersion`(**한 트랜잭션**). 상태가 `시행예정`이 아니면(그 사이 전환·철회) 아무것도 안 하고 완료 | — |
| `TRANSITION(lawId, 시행일)` | 상태 `시행예정 → 시행중`, 같은 법령의 더 오래된 `시행중` 행 → `과거`, 이 버전의 pending 색인 제거(`replaceVersion(…, [])`) | 같은 법령의 **남은 시행예정본마다 `SYNC`** 등록(기준선이 바뀌어 diff 재생성 필요) |
| `PURGE(lawId, 시행일)` | 상태 → `철회`, 색인 제거. 행은 남긴다 | — |

## 동시성 · 호출 한도
- **공용 워커 풀** — 크기는 설정값(`lia.batch.workers`, 기본 2). 가져가기 우선순위: `TRANSITION`·`PURGE`(DB만) → `SYNC` → `INDEX`.
  ```sql
  UPDATE ingest_tasks SET status = 'RUNNING', attempts = attempts + 1, last_attempt_run = :runId, updated_at = now()
  WHERE task_id = (SELECT task_id FROM ingest_tasks
                   WHERE status = 'READY' AND last_attempt_run IS DISTINCT FROM :runId
                     AND law_id <> ALL(:busyLawIds)          -- 지금 처리 중인 법령 제외
                   ORDER BY CASE kind WHEN 'TRANSITION' THEN 0 WHEN 'PURGE' THEN 0
                                      WHEN 'SYNC' THEN 1 ELSE 2 END, task_id
                   FOR UPDATE SKIP LOCKED LIMIT 1)
  RETURNING *;
  ```
- **같은 법령의 작업은 동시에 돌지 않는다** — 시행예정본이 여럿인 법령(D43)은 TRANSITION과 형제 SYNC가 같은 기준선 행을 건드린다. 실행 전체 잠금으로 프로세스가 하나뿐이라 **프로세스 안에서** 처리 중인 법령 집합(`busyLawIds`)을 들고, 가져가기 쿼리가 그 법령을 **처음부터 제외**한다. 두 워커가 같은 법령을 동시에 가져가지 않도록 "가져가기 + 집합 등록"은 프로세스 안에서 직렬로 한다(가져가기는 짧은 쿼리라 병목이 아니다).
  - 가져간 뒤 돌려놓는 방식은 쓰지 않는다 — 가져가는 순간 `last_attempt_run`이 찍혀 그 작업이 이번 실행에서 다시 잡히지 않기 때문이다.
- **API별 최소 호출 간격**(`ApiRateLimiter`) — 국가법령정보·임베딩 각각 설정값. 워커 수와 무관하게 API로 나가는 속도를 제한한다. 국가법령정보의 공식 호출 한도는 확인 후 기본값에 반영(현재는 보수적으로).
- **429** — 백오프, 응답에 `Retry-After`가 있으면 따른다.

## Error Handling
| 분류 | 해당 | 처리 |
|---|---|---|
| 일시적 | 네트워크·타임아웃, 커넥터 재시도 후 남은 5xx, 429, DB 연결 오류 | `READY`로 돌려 다음 실행에서 재시도. `attempts ≥ 3`이면 `DEAD` |
| 영구 | API 논리 오류(`LawApiException`), 정규화·파싱 실패, 제약 위반(코드 버그) | 즉시 `DEAD` |
| 모름 | 그 밖의 예외 | 일시적으로 취급(3회면 격리) |

- **실행당 1회** — 가져가기가 `last_attempt_run = 이번 실행`인 작업을 제외한다.
- **목록 조회 전체 실패** — DB만으로 판정하는 `TRANSITION`은 등록·처리하고, 신규·변경·사라짐 판정은 건너뛴다(`list_complete=false`, 종료 코드 2).
- **색인 실패** — `replaceVersion`이 한 트랜잭션이라 롤백되면 **이전 청크가 그대로 남는다**(검색이 비지 않음).
- **종료 신호(SIGTERM)** — 새 작업을 가져가지 않고 진행 중 작업은 제한 시간 안에서 마친 뒤 종료. 남은 `RUNNING`은 다음 실행 준비 단계에서 복구.
- 커넥터의 요청 단위 재시도(5xx·네트워크 지수 백오프)는 그대로 둔다 — 큐 재시도는 그 위의 작업 단위 재시도다.

## Observability
**한 번 돌고 끝나는 잡이라 Prometheus가 긁기 전에 프로세스가 사라진다.** 그래서 기준 출처를 바꾼다.

| 수단 | 용도 |
|---|---|
| **`ingest_runs` (기준 출처)** | 실행별 결과·건수·소요 시간. Grafana가 Postgres 데이터 소스로 직접 조회 |
| 트레이스(OTLP push → Tempo) | 실행 span 아래 작업 span(`kind` 태그), 그 아래 기존 `lia.ingest`·`lia.normalize`·`lia.diff` |
| 구조화 로그 | 작업마다 한 줄: 종류·법령·결과·소요 시간·오류 |

Pushgateway는 두지 않는다. **신호:** `DEAD` ≥ 1 · 마지막 성공 실행이 26시간 넘게 없음 · 목록 미완주 연속 · 가장 오래된 `READY`의 나이 · 색인 지연(최신 `INDEX`가 완료 안 된 시행예정 법령 수).

## Invariants
- 한 시점에 배치는 **하나만** 돈다(advisory lock).
- 같은 `(종류, 법령 버전)`의 미완료·격리 작업은 **하나만** 존재한다(부분 유니크 인덱스).
- 같은 법령의 작업은 **동시에 처리되지 않는다.**
- **두 번 연속 실행해도 결과가 같다** — 두 번째 실행은 등록·쓰기가 없다(MST·revision 증분).
- 시행예정 색인에는 `status = 시행예정`인 버전만 있다(전환·철회 시 제거).
- 사라짐 판정은 목록을 끝까지 받은 실행에서, 두 번 연속 미확인일 때만 한다.

## Side Effects
- **DB 쓰기**(`law_versions`·`ingest_tasks`·`ingest_runs`·`vector_store`) · **외부 API 호출**(국가법령정보, 임베딩) · 로그·트레이스.

## Design Constraints
- **오프라인 전용**(D40) — 온라인 요청 경로에서 호출하지 않는다.
- **Layer A 선계산(#38) 연결점** — 붙일 때는 작업 종류(예: `PRECOMPUTE_A`)를 추가하고, `SYNC`가 revision 변경 시 `INDEX`와 함께 등록하게 한다. 큐·재시도·격리·한도는 그대로 재사용된다.
- **Spring Batch 미사용** — 하루 1회·수백 건 규모에 메타 테이블과 개념이 과하다.
- **임베딩 Batch API 미사용** — 임베딩은 전체 재색인도 저비용으로 추정되고, revision 증분이면 바뀐 법령만 다시 임베딩한다.
- 규모가 커지면: 메타데이터 필터 삭제의 전체 스캔(현 `replaceVersion`) → 메타데이터 인덱스 또는 버전 테이블 검토.

## 검증
| 대상 | 방법 | 확인할 것 |
|---|---|---|
| `Discoverer` | 표 기반 단위 테스트, 가짜 커넥터 | 신규·MST 변경·동일·시행 도래·첫 미확인·두 번째 미확인·목록 미완주·`--full` |
| `TaskQueue` | Testcontainers | 중복 등록 무시, `DEAD`가 재등록 차단, `SKIP LOCKED` 동시 가져가기, `RUNNING` 복구, 이번 실행 시도 작업 제외, 우선순위 |
| 핸들러 | 가짜 커넥터(샘플 fixture) + Testcontainers | SYNC: revision 같으면 저장·INDEX 없음. TRANSITION: 형제 SYNC·`과거` 전환·색인 제거. INDEX: 실패 시 이전 청크 유지. PURGE: `철회`·색인 제거 |
| 잡 전체 | Testcontainers + 가짜 커넥터 + 가짜 임베딩 | 두 번 실행 = 같은 결과. 실패 주입: 한 법령만 일시 오류 → 나머지 성공, 3회 후 `DEAD`, 영구 오류 즉시 `DEAD`. 잠금 경합 시 두 번째 실행 즉시 종료 |
| 실제 API | 옵트인 스모크(키·플래그 있을 때만) | `run --dry-run`으로 실제 목록 분류만 확인 |

## 의존 / 관련
[[IngestService]] · [[RAGIndexer]] · [[LawStore]] · [[ChunkStore]] · [[LawConnector]] · [[concurrency-and-reliability]] §2 · [[deployment]] · D16·D40·D43·D54·D55·D62 · #38·#40
