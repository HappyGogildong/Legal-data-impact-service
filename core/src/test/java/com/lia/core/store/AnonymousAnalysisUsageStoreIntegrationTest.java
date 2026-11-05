package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.testsupport.TestDatabases;

/** 익명 분석 하루 상한(D81) — 조건부 upsert. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class AnonymousAnalysisUsageStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM anonymous_analysis_usage_daily").update();
    }

    @Test
    void 상한까지는_통과하고_넘으면_거부한다() {
        AnonymousAnalysisUsageStore quota = new AnonymousAnalysisUsageStore(jdbc, 2);

        assertTrue(quota.tryConsume());
        assertTrue(quota.tryConsume());
        assertFalse(quota.tryConsume(), "3번째는 거부");
        assertEquals(2, jdbc.sql("SELECT count FROM anonymous_analysis_usage_daily").query(Integer.class).single(),
                "거부는 횟수를 늘리지 않는다");
    }

    @Test
    void 어제_상한에_닿았어도_오늘은_새로_센다() {
        jdbc.sql("""
                INSERT INTO anonymous_analysis_usage_daily (usage_date, count)
                VALUES (CAST(now() AT TIME ZONE 'Asia/Seoul' AS date) - 1, 2)
                """).update();

        assertTrue(new AnonymousAnalysisUsageStore(jdbc, 2).tryConsume());
    }

    @Test
    void 상한은_1_이상이어야_한다() {
        assertThrows(IllegalArgumentException.class, () -> new AnonymousAnalysisUsageStore(jdbc, 0));
    }
}
