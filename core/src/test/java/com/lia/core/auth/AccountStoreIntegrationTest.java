package com.lia.core.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

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
 * AccountStore 통합 테스트 — 실 Postgres(Testcontainers)에 create·findByProvider·delete.
 * query(Account.class) 컬럼→레코드 매핑(UUID·timestamptz·nullable email) 라운드트립 검증.
 * Docker 없으면 자동 스킵.
 */
@Testcontainers(disabledWithoutDocker = true)
class AccountStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static AccountStore store;

    @BeforeAll
    static void setup() {
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        store = new AccountStore(JdbcClient.create(ds));
    }

    @Test
    @DisplayName("create 후 findByProvider 로 동일 계정 회수(매핑 라운드트립)")
    void create_findByProvider_라운드트립() {
        Account created = store.create("kakao", "subject-1", "user@example.com");

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
    void create_nullEmail() {
        store.create("naver", "subject-2", null);

        Account r = store.findByProvider("naver", "subject-2").orElseThrow();
        assertNull(r.email(), "nullable email");
    }

    @Test
    @DisplayName("같은 (provider, providerId) 재로그인은 기존 userId 회수 — 멱등")
    void findByProvider_멱등() {
        Account first = store.create("google", "subject-3", "g@example.com");

        // 재로그인: 새로 만들지 않고 findByProvider 로 같은 userId 를 얻는다
        Account again = store.findByProvider("google", "subject-3").orElseThrow();
        assertEquals(first.userId(), again.userId());
    }

    @Test
    @DisplayName("delete 는 계정 파기 — 이후 조회 empty")
    void delete_파기() {
        Account created = store.create("kakao", "subject-4", null);
        assertTrue(store.findByProvider("kakao", "subject-4").isPresent());

        store.delete(created.userId());

        assertTrue(store.findByProvider("kakao", "subject-4").isEmpty(), "파기 후 조회 불가");
    }
}
