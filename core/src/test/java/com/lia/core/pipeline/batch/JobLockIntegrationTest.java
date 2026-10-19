package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.testsupport.TestDatabases;

/** 실행 잠금 — 한 번에 하나만. 닫으면 다음 실행이 잡을 수 있다. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class JobLockIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static DataSource ds;

    @BeforeAll
    static void setup() {
        ds = TestDatabases.migrate(PG);
    }

    @Test
    @DisplayName("이미 잡혀 있으면 두 번째는 empty, 닫으면 다시 잡힌다")
    void 단일실행() {
        Optional<JobLock> first = JobLock.tryAcquire(ds);
        assertTrue(first.isPresent());

        assertTrue(JobLock.tryAcquire(ds).isEmpty(), "다른 실행이 잡고 있다");

        first.get().close();
        Optional<JobLock> again = JobLock.tryAcquire(ds);
        assertTrue(again.isPresent());
        again.get().close();
    }
}
