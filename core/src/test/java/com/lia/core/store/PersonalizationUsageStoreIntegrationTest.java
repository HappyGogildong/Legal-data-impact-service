package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.testsupport.TestDatabases;

/** 하루 개인화 한도(D69) — 경계·날짜 바뀜·계정 삭제 cascade. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class PersonalizationUsageStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;
    UUID userId;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
    }

    @BeforeEach
    void account() {
        jdbc.sql("DELETE FROM personalization_usage_daily").update();
        userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (user_id, provider, provider_id, created_at) VALUES (:id, 'kakao', :sub, now())")
                .param("id", userId).param("sub", userId.toString()).update();
    }

    @Test
    void 한도까지는_통과하고_넘으면_거부한다() {
        PersonalizationUsageStore quota = new PersonalizationUsageStore(jdbc, 3);

        assertTrue(quota.tryConsume(userId));
        assertTrue(quota.tryConsume(userId));
        assertTrue(quota.tryConsume(userId));
        assertFalse(quota.tryConsume(userId), "4번째는 거부");
        assertEquals(3, jdbc.sql("SELECT count FROM personalization_usage_daily WHERE user_id = :id")
                .param("id", userId).query(Integer.class).single(), "거부는 횟수를 늘리지 않는다");
    }

    @Test
    void 어제_한도를_다_썼어도_오늘은_새로_센다() {
        jdbc.sql("""
                INSERT INTO personalization_usage_daily (user_id, usage_date, count)
                VALUES (:id, CAST(now() AT TIME ZONE 'Asia/Seoul' AS date) - 1, 3)
                """).param("id", userId).update();

        assertTrue(new PersonalizationUsageStore(jdbc, 3).tryConsume(userId));
    }

    @Test
    void 계정을_지우면_한도_기록도_지워진다() {
        new PersonalizationUsageStore(jdbc, 3).tryConsume(userId);

        jdbc.sql("DELETE FROM accounts WHERE user_id = :id").param("id", userId).update();

        assertEquals(0, jdbc.sql("SELECT count(*) FROM personalization_usage_daily").query(Integer.class).single());
    }

    @Test
    void 한도는_1_이상이어야_한다() {
        assertThrows(IllegalArgumentException.class, () -> new PersonalizationUsageStore(jdbc, 0));
    }
}
