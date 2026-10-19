package com.lia.core.pipeline.batch;

import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

import tools.jackson.databind.ObjectMapper;

/**
 * 실행 기록 — {@code ingest_runs}(D62). 1회성 잡이라 Prometheus 가 긁지 못하므로 이 테이블이 실행 관측의
 * 기준 출처다(Grafana 가 Postgres 를 직접 조회).
 */
public class IngestRunStore {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public IngestRunStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * 끝나지 못한 실행(강제 종료 등)을 FAILED 로 닫는다 — "종료된 실행은 finished_at·outcome 을 가진다"를
     * 다음 실행이 보장. 실행 잠금을 잡은 뒤, 새 실행 행을 만들기 전에 부른다. 닫은 수.
     */
    public int closeAbandoned() {
        return jdbc.sql("""
                UPDATE ingest_runs
                SET finished_at = now(), outcome = 'FAILED', counts = counts || '{"abandoned": 1}'::jsonb
                WHERE finished_at IS NULL
                """)
                .update();
    }

    /** 실행 시작 — run_id 반환. */
    public long start(String command) {
        return jdbc.sql("INSERT INTO ingest_runs (command, started_at) VALUES (:command, now()) RETURNING run_id")
                .param("command", command)
                .query(Long.class)
                .single();
    }

    /** 실행 종료 — 결과·목록 완주 여부(탐색 없는 명령은 null)·건수. */
    public void finish(long runId, RunOutcome outcome, Boolean listComplete, Map<String, Integer> counts) {
        jdbc.sql("""
                UPDATE ingest_runs
                SET finished_at = now(), outcome = :outcome, list_complete = :listComplete,
                    counts = CAST(:counts AS jsonb)
                WHERE run_id = :runId
                """)
                .param("outcome", outcome.name())
                .param("listComplete", listComplete)
                .param("counts", json.writeValueAsString(counts))
                .param("runId", runId)
                .update();
    }
}
