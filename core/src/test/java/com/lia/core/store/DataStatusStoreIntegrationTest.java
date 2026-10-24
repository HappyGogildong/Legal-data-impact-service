package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Law;
import com.lia.core.testsupport.TestDatabases;
import com.lia.core.testsupport.TestLaws;

/** 데이터 현황 — 갱신 시각 정의(목록 완주 run 만)와 시행예정 수. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class DataStatusStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;
    static LawStore lawStore;
    static DataStatusStore store;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
        lawStore = new LawStore(jdbc, new ObjectMapper());
        store = new DataStatusStore(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM ingest_task_attempts").update();
        jdbc.sql("DELETE FROM ingest_tasks").update();
        jdbc.sql("DELETE FROM ingest_runs").update();
        jdbc.sql("DELETE FROM law_versions").update();
    }

    private void run(String command, String outcome, boolean listComplete, OffsetDateTime finishedAt) {
        jdbc.sql("""
                INSERT INTO ingest_runs (command, started_at, finished_at, outcome, list_complete)
                VALUES (:command, :finished, :finished, :outcome, :complete)
                """)
                .param("command", command).param("finished", finishedAt)
                .param("outcome", outcome).param("complete", listComplete)
                .update();
    }

    @Test
    @DisplayName("빈 DB — 갱신 시각 없음, 시행예정 0")
    void 빈DB() {
        DataStatusStore.DataStatus status = store.read();

        assertNull(status.lawsUpdatedAt());
        assertEquals(0, status.pendingLawCount());
    }

    @Test
    @DisplayName("시행예정 버전만 센다")
    void 시행예정만() {
        lawStore.upsert(TestLaws.law("A", "M1", Law.Status.시행예정, LocalDate.of(2027, 1, 1)));
        lawStore.upsert(TestLaws.law("A", "M2", Law.Status.시행예정, LocalDate.of(2027, 6, 1)));
        lawStore.upsert(TestLaws.law("B", "M3", Law.Status.시행중, LocalDate.of(2020, 1, 1)));

        assertEquals(2, store.read().pendingLawCount());
    }

    @Test
    @DisplayName("갱신 시각 — 목록 완주 run·run --full 의 SUCCESS·PARTIAL 만, dry-run·미완주·FAILED·단건은 제외")
    void 갱신시각_정의() {
        OffsetDateTime counted = OffsetDateTime.of(2026, 10, 23, 4, 12, 0, 0, ZoneOffset.ofHours(9));
        run("run", "SUCCESS", true, counted.minusDays(1));
        run("run --full", "PARTIAL", true, counted);
        run("run --dry-run", "SUCCESS", true, counted.plusHours(1));
        run("run", "PARTIAL", false, counted.plusHours(2));
        run("run", "FAILED", true, counted.plusHours(3));
        run("sync 001809 2026-08-04", "SUCCESS", true, counted.plusHours(4));   // 명령으로 제외되는지 보려고 완주 표시

        assertEquals(counted.toInstant(), store.read().lawsUpdatedAt().toInstant());
    }
}
