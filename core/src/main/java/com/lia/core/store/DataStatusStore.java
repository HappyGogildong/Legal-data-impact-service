package com.lia.core.store;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 데이터 현황(화면 S15·S05) — 공개용 <b>시각·개수만</b>. 운영 세부(격리 작업 수·오류)는 Grafana(cloud-infra §4).
 * 설계: docs/components/web/DataStatusApi.md
 */
@Repository
public class DataStatusStore {

    private final JdbcClient jdbc;

    public DataStatusStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public DataStatus read() {
        // 목록 전체를 받아 갱신을 마친 마지막 실행 — dry-run·단건 sync·index·미완주·실패 제외
        OffsetDateTime updatedAt = jdbc.sql("""
                SELECT max(finished_at) FROM ingest_runs
                WHERE command IN ('run', 'run --full')
                  AND outcome IN ('SUCCESS', 'PARTIAL')
                  AND list_complete
                """)
                .query(OffsetDateTime.class)
                .optional()
                .orElse(null);
        int pending = jdbc.sql("SELECT count(*) FROM law_versions WHERE status = '시행예정'")
                .query(Integer.class)
                .single();
        return new DataStatus(updatedAt, pending);
    }

    /**
     * @param lawsUpdatedAt   목록 전체를 받아 갱신을 마친 마지막 배치 시각. 없으면 null
     * @param pendingLawCount 수록된 시행예정 법령 버전 수
     */
    public record DataStatus(OffsetDateTime lawsUpdatedAt, int pendingLawCount) {}
}
