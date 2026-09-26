package com.lia.core.auth;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 계정 저장소 — {@code accounts} 조회·생성·파기(Auth, D60. [[LawStore]] 패턴).
 *
 * <p>재로그인은 {@code findByProvider} 로 기존 {@code userId} 를 멱등 회수한다.
 * {@code delete} 는 계정 파기(D41). 스키마: {@code db/migration/V2__accounts.sql}.
 */
@Repository
public class AccountStore {

    private final JdbcClient jdbc;

    public AccountStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 소셜 신원으로 기존 계정 조회(재로그인 시 userId 회수). */
    public Optional<Account> findByProvider(String provider, String providerId) {
        return jdbc.sql("""
                SELECT user_id, provider, provider_id, email, created_at
                FROM accounts WHERE provider = :provider AND provider_id = :providerId
                """)
                .param("provider", provider)
                .param("providerId", providerId)
                .query(Account.class)
                .optional();
    }

    /**
     * 로그인 시 계정 회수, 없으면 생성(최초 로그인). userId 는 서버 발급(UUID), email 은 알림용(nullable).
     *
     * <p>경합 안전: 동시 최초 로그인(더블클릭·두 탭)이 둘 다 조회 miss 여도 {@code ON CONFLICT DO NOTHING}
     * 이 UNIQUE(provider, provider_id) 충돌을 흡수하고, 재조회가 먼저 들어간 행을 돌려준다 — 같은 userId.
     */
    public Account findOrCreate(String provider, String providerId, String email) {
        return findByProvider(provider, providerId).orElseGet(() -> {
            jdbc.sql("""
                    INSERT INTO accounts (user_id, provider, provider_id, email, created_at)
                    VALUES (:userId, :provider, :providerId, :email, :createdAt)
                    ON CONFLICT (provider, provider_id) DO NOTHING
                    """)
                    .param("userId", UUID.randomUUID())
                    .param("provider", provider)
                    .param("providerId", providerId)
                    .param("email", email)
                    .param("createdAt", OffsetDateTime.now(ZoneOffset.UTC))
                    .update();
            return findByProvider(provider, providerId).orElseThrow();
        });
    }

    /** 계정 파기(D41). 프로필은 FK cascade 로 함께 삭제([[UserProfile]] ②). */
    public void delete(UUID userId) {
        jdbc.sql("DELETE FROM accounts WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }
}
