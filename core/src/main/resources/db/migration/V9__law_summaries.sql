-- 쉬운 요약 선계산(D67) — 시행예정 버전마다 1행. 분석과 같은 엔진·인용 검증을 통과한 결과(ImpactResult JSON)만.
-- 정본 revision·프롬프트 버전이 지금과 다르면 낡은 것으로 보고 읽지 않는다(다음 적재가 다시 만든다).
CREATE TABLE law_summaries (
    law_id          text        NOT NULL,
    effective_date  date        NOT NULL,
    revision        text        NOT NULL,   -- 만들 때의 정본 revision
    prompt_version  text        NOT NULL,   -- 만들 때의 분석 프롬프트 버전
    model           text        NOT NULL,
    result          jsonb       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (law_id, effective_date)
);

-- 배치 작업 종류에 SUMMARIZE 추가(D62 큐 재사용)
ALTER TABLE ingest_tasks DROP CONSTRAINT ingest_tasks_kind_check;
ALTER TABLE ingest_tasks ADD CONSTRAINT ingest_tasks_kind_check
    CHECK (kind IN ('SYNC', 'INDEX', 'SUMMARIZE', 'TRANSITION', 'PURGE'));
