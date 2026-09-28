package com.lia.core.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * UserProfileStore 통합 테스트 — 실 Postgres(Testcontainers). 동의가 행을 만들고, 속성 교체는 동의를
 * 보존하며, 동의 없이는 행이 생기지 않는다. text[]·라벨 라운드트립, accounts 삭제 cascade, age CHECK.
 * Docker 없으면 자동 스킵.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserProfileStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;
    static UserProfileStore store;

    /** 특수문자 라벨(·, +, 공백, 숫자 시작)을 모두 포함한 프로필. */
    static final UserProfile FULL = new UserProfile(
            Set.of(Purpose.생활주거, Purpose.관심사모니터링), 29, Occupation.사무,
            EmploymentType.무직은퇴, HouseholdType.부부자녀, HousingType.전세, Sido.서울특별시);

    @BeforeAll
    static void setup() {
        Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        store = new UserProfileStore(jdbc);
    }

    /** FK 대상 계정 — AccountStore 시그니처에 묶이지 않게 SQL 로 직접 만든다. */
    private static UUID newAccount() {
        UUID userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (user_id, provider, provider_id, created_at) VALUES (:id, 'kakao', :sub, now())")
                .param("id", userId).param("sub", userId.toString())
                .update();
        return userId;
    }

    @Test
    @DisplayName("recordConsent — 행이 없으면 속성이 빈 프로필을 만든다")
    void recordConsent_빈행생성() {
        UUID userId = newAccount();

        store.recordConsent(userId, "v1");

        StoredProfile stored = store.find(userId).orElseThrow();
        assertEquals(UserProfile.empty(), stored.profile());
        assertEquals("v1", stored.policyVersion());
        assertNotNull(stored.consentedAt());
        assertNotNull(stored.updatedAt());
    }

    @Test
    @DisplayName("replaceAttributes — 속성 전체 교체, 동의 버전·일시는 그대로(라벨·text[] 라운드트립)")
    void replaceAttributes_동의보존() {
        UUID userId = newAccount();
        store.recordConsent(userId, "v1");
        var consentedAt = store.find(userId).orElseThrow().consentedAt();

        assertTrue(store.replaceAttributes(userId, FULL));

        StoredProfile stored = store.find(userId).orElseThrow();
        assertEquals(FULL, stored.profile());
        assertEquals("v1", stored.policyVersion());
        assertEquals(consentedAt, stored.consentedAt(), "속성 수정이 동의 일시를 바꾸지 않는다");
    }

    @Test
    @DisplayName("재동의 — 버전·일시만 갱신, 속성 보존")
    void 재동의_속성보존() {
        UUID userId = newAccount();
        store.recordConsent(userId, "v1");
        store.replaceAttributes(userId, FULL);

        store.recordConsent(userId, "v2");

        StoredProfile stored = store.find(userId).orElseThrow();
        assertEquals("v2", stored.policyVersion());
        assertEquals(FULL, stored.profile());
    }

    @Test
    @DisplayName("동의 없이 replaceAttributes → false, 행이 생기지 않는다")
    void 동의없으면_교체안됨() {
        UUID userId = newAccount();

        assertFalse(store.replaceAttributes(userId, FULL));
        assertTrue(store.find(userId).isEmpty());
    }

    @Test
    @DisplayName("delete — 파기, 두 번 불러도 예외 없음")
    void delete_멱등() {
        UUID userId = newAccount();
        store.recordConsent(userId, "v1");

        store.delete(userId);
        store.delete(userId);

        assertTrue(store.find(userId).isEmpty());
    }

    @Test
    @DisplayName("계정 삭제 시 프로필도 cascade 로 삭제")
    void 계정삭제_cascade() {
        UUID userId = newAccount();
        store.recordConsent(userId, "v1");

        jdbc.sql("DELETE FROM accounts WHERE user_id = :id").param("id", userId).update();

        assertTrue(store.find(userId).isEmpty());
    }

    @Test
    @DisplayName("DB CHECK — 도메인을 우회해 age 13 을 넣으면 거부")
    void age_CHECK() {
        UUID userId = newAccount();
        store.recordConsent(userId, "v1");

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.sql("UPDATE user_profiles SET age = 13 WHERE user_id = :id").param("id", userId).update());
    }
}
