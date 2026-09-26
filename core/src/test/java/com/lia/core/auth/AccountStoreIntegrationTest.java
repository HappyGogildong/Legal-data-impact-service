package com.lia.core.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * AccountStore 통합 테스트 — 실 Postgres(Testcontainers)에 findOrCreate·findByProvider·delete.
 * query(Account.class) 컬럼→레코드 매핑(UUID·timestamptz·nullable email) 라운드트립과
 * 동시 최초 로그인 경합(ON CONFLICT) 검증. Docker 없으면 자동 스킵.
 */
@Testcontainers(disabledWithoutDocker = true)
class AccountStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;
    static AccountStore store;

    @BeforeAll
    static void setup() {
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = JdbcClient.create(ds);
        store = new AccountStore(jdbc);
    }

    @Test
    @DisplayName("findOrCreate 후 findByProvider 로 동일 계정 회수(매핑 라운드트립)")
    void findOrCreate_findByProvider_라운드트립() {
        Account created = store.findOrCreate("kakao", "subject-1", "user@example.com");

        Optional<Account> got = store.findByProvider("kakao", "subject-1");

        assertTrue(got.isPresent());
        Account r = got.get();
        assertEquals(created.userId(), r.userId(), "UUID 보존");
        assertEquals("kakao", r.provider());
        assertEquals("subject-1", r.providerId());
        assertEquals("user@example.com", r.email());
        assertNotNull(r.createdAt(), "timestamptz 매핑");
    }

    @Test
    @DisplayName("email 미제공(null) 계정도 정상 저장·조회")
    void findOrCreate_nullEmail() {
        store.findOrCreate("naver", "subject-2", null);

        Account r = store.findByProvider("naver", "subject-2").orElseThrow();
        assertNull(r.email(), "nullable email");
    }

    @Test
    @DisplayName("같은 (provider, providerId) 재로그인은 기존 userId 회수 — 멱등")
    void findOrCreate_멱등() {
        Account first = store.findOrCreate("google", "subject-3", "g@example.com");
        Account again = store.findOrCreate("google", "subject-3", "g@example.com");

        assertEquals(first.userId(), again.userId());
        assertEquals(1, countRows("google", "subject-3"), "중복 행 없음");
    }

    @Test
    @DisplayName("동시 최초 로그인 — 전부 같은 userId, 행 1개, 예외 없음(ON CONFLICT 흡수)")
    void findOrCreate_동시경합() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<UUID> login = () -> {
                start.await();   // 동시에 출발시켜 조회 miss 가 겹치게 한다
                return store.findOrCreate("kakao", "race-subject", null).userId();
            };
            List<Future<UUID>> results = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(login)).toList();
            start.countDown();

            UUID first = results.get(0).get();
            for (Future<UUID> result : results) {
                assertEquals(first, result.get(), "모든 동시 로그인이 같은 userId");
            }
            assertEquals(1, countRows("kakao", "race-subject"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("delete 는 계정 파기 — 이후 조회 empty")
    void delete_파기() {
        Account created = store.findOrCreate("kakao", "subject-4", null);
        assertTrue(store.findByProvider("kakao", "subject-4").isPresent());

        store.delete(created.userId());

        assertTrue(store.findByProvider("kakao", "subject-4").isEmpty(), "파기 후 조회 불가");
    }

    private static long countRows(String provider, String providerId) {
        return jdbc.sql("SELECT count(*) FROM accounts WHERE provider = :p AND provider_id = :id")
                .param("p", provider).param("id", providerId)
                .query(Long.class).single();
    }
}
