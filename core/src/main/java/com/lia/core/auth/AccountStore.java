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

    /** 최초 로그인 시 계정 생성. userId 는 서버가 발급(UUID). email 은 알림용(nullable). */
    public Account create(String provider, String providerId, String email) {
        Account account = new Account(
                UUID.randomUUID(), provider, providerId, email,
                OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.sql("""
                INSERT INTO accounts (user_id, provider, provider_id, email, created_at)
                VALUES (:userId, :provider, :providerId, :email, :createdAt)
                """)
                .param("userId", account.userId())
                .param("provider", account.provider())
                .param("providerId", account.providerId())
                .param("email", account.email())
                .param("createdAt", account.createdAt())
                .update();
        return account;
    }

    /** 계정 파기(D41). 프로필은 FK cascade 로 함께 삭제([[UserProfile]] ②). */
    public void delete(UUID userId) {
        jdbc.sql("DELETE FROM accounts WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }
}
