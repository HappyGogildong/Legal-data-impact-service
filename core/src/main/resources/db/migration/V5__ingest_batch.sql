-- 적재·RAG 색인 배치(D62) — 사라짐 판정 컬럼 + 실행 기록 + 작업 큐.
-- law_versions.status 값 확장(text 컬럼, 도메인 Law.Status 가 SSOT):
--   시행중 · 시행예정 · 과거(대체된 시행중본) · 철회(목록에서 사라진 시행예정본). 행은 지우지 않는다.
ALTER TABLE law_versions ADD COLUMN missing_since date;   -- 목록에서 처음 안 보인 날(두 번째 미확인 시 PURGE)

CREATE TABLE ingest_runs (                          -- 실행 단위
    run_id        bigserial   PRIMARY KEY,
    command       text        NOT NULL,             -- run · run --full · run --dry-run · sync … · reindex-all · requeue-dead
    started_at    timestamptz NOT NULL,
    finished_at   timestamptz,
    outcome       text        CHECK (outcome IN ('SUCCESS', 'PARTIAL', 'FAILED')),
    list_complete boolean,                          -- 목록을 totalCnt 까지 받았는가(사라짐 판정 전제). 탐색 없는 명령은 NULL
    counts        jsonb       NOT NULL DEFAULT '{}', -- listed · planned.<종류> · enqueued.<종류> · done · failed · dead …
    CHECK ((finished_at IS NULL) = (outcome IS NULL)) -- 종료 기록은 둘 다 있거나 둘 다 없다
);

CREATE TABLE ingest_tasks (                         -- 작업의 현재 상태
    task_id          bigserial   PRIMARY KEY,
    kind             text        NOT NULL CHECK (kind IN ('SYNC', 'INDEX', 'TRANSITION', 'PURGE')),
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
-- 같은 (종류, 법령 버전)의 미완료·격리 작업은 하나만 — 탐색이 격리(DEAD) 작업을 다시 등록하지 못하게 DEAD 포함
CREATE UNIQUE INDEX ingest_tasks_open_uq ON ingest_tasks (kind, law_id, effective_date)
    WHERE status IN ('READY', 'RUNNING', 'DEAD');
CREATE INDEX ingest_tasks_status ON ingest_tasks (status);

CREATE TABLE ingest_task_attempts (                 -- 시도 이력(로그는 docker compose run --rm 과 함께 사라짐)
    attempt_id    bigserial   PRIMARY KEY,
    task_id       bigint      NOT NULL REFERENCES ingest_tasks ON DELETE CASCADE, -- DONE 보존 정리 시 함께 삭제
    run_id        bigint      NOT NULL REFERENCES ingest_runs,
    attempt       int         NOT NULL,             -- 이 시도를 포함한 ingest_tasks.attempts
    started_at    timestamptz NOT NULL,
    finished_at   timestamptz,
    outcome       text        CHECK (outcome IN ('DONE', 'RETRY', 'DEAD', 'ABANDONED')),
    error_type    text,                             -- PERMANENT:<예외> · TRANSIENT:<예외> · ABANDONED
    error_message text
);
CREATE INDEX ingest_task_attempts_task ON ingest_task_attempts (task_id);
