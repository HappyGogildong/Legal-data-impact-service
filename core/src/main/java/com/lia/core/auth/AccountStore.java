package com.lia.core.auth;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 계정 저장소 — {@code accounts} 조회·생성·알림 이메일·파기(Auth, D60·D61. [[LawStore]] 패턴).
 *
 * <p>로그인은 {@link #findOrCreate} 로 계정만 만들고 email 은 저장하지 않는다. email 은
 * {@link #setNotificationEmail}(알림 동의)로만 들어가며 동의 일시와 함께 저장된다.
 * 스키마: {@code db/migration/V2__accounts.sql}·{@code V4__account_email_consent.sql}.
 */
@Repository
public class AccountStore {

    private static final String COLUMNS = "user_id, provider, provider_id, email, email_consented_at, created_at";

    private final JdbcClient jdbc;

    public AccountStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 소셜 신원으로 기존 계정 조회(재로그인 시 userId 회수). */
    public Optional<Account> findByProvider(String provider, String providerId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts WHERE provider = :provider AND provider_id = :providerId")
                .param("provider", provider)
                .param("providerId", providerId)
                .query(Account.class)
                .optional();
    }

    /** 세션 userId 로 계정 조회. */
    public Optional<Account> find(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts WHERE user_id = :userId")
                .param("userId", userId)
                .query(Account.class)
                .optional();
    }

    /**
     * 로그인 시 계정 회수, 없으면 생성(최초 로그인, email 없이). userId 는 서버 발급(UUID).
     *
     * <p>경합 안전: 동시 최초 로그인(더블클릭·두 탭)이 둘 다 조회 miss 여도 {@code ON CONFLICT DO NOTHING}
     * 이 UNIQUE(provider, provider_id) 충돌을 흡수하고, 재조회가 먼저 들어간 행을 돌려준다 — 같은 userId.
     */
    public Account findOrCreate(String provider, String providerId) {
        return findByProvider(provider, providerId).orElseGet(() -> {
            jdbc.sql("""
                    INSERT INTO accounts (user_id, provider, provider_id, created_at)
                    VALUES (:userId, :provider, :providerId, :createdAt)
                    ON CONFLICT (provider, provider_id) DO NOTHING
                    """)
                    .param("userId", UUID.randomUUID())
                    .param("provider", provider)
                    .param("providerId", providerId)
                    .param("createdAt", OffsetDateTime.now(ZoneOffset.UTC))
                    .update();
            return findByProvider(provider, providerId).orElseThrow();
        });
    }

    /**
     * 알림 이메일 수신 동의 — email 과 동의 일시(지금)를 함께 저장.
     *
     * @return 계정이 있어 갱신됐으면 true. 계정이 없으면(다른 기기에서 계정 삭제 등) false.
     */
    public boolean setNotificationEmail(UUID userId, String email) {
        int updated = jdbc.sql("UPDATE accounts SET email = :email, email_consented_at = :now WHERE user_id = :userId")
                .param("email", email)
                .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                .param("userId", userId)
                .update();
        return updated == 1;
    }

    /** 알림 동의 철회 — email·동의 일시 모두 null. 멱등. */
    public void clearNotificationEmail(UUID userId) {
        jdbc.sql("UPDATE accounts SET email = NULL, email_consented_at = NULL WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    /** 계정 파기(D41). 프로필은 FK cascade 로 함께 삭제. */
    public void delete(UUID userId) {
        jdbc.sql("DELETE FROM accounts WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }
}
