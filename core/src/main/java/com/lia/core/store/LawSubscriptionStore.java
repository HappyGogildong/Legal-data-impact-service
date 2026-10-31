package com.lia.core.store;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 법령 구독(D74) — {@code law_subscriptions}, 법령(lawId) 단위, 알림 시점 하나. 추가는 계정 행을 잠근 트랜잭션에서
 * (북마크와 같은 방식) — 이미 구독했으면 시점만 바꾸고 한도와 무관. 목록은 다음 시행일(가장 이른 시행예정본)을 붙인다.
 * 설계: docs/components/web/SubscriptionApi.md
 */
public class LawSubscriptionStore {

    public enum SubscribeResult { SUBSCRIBED, UPDATED, LIMIT, LAW_NOT_FOUND, ACCOUNT_NOT_FOUND }

    /** @param nextEffectiveDate 가장 이른 시행예정본의 시행일 — 없으면 null(다음 개정을 기다리는 중) */
    public record SubscribedLaw(String lawId, String title, int notifyBefore, OffsetDateTime createdAt,
                                LocalDate nextEffectiveDate) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final int maxPerUser;

    public LawSubscriptionStore(JdbcClient jdbc, TransactionTemplate transactions, int maxPerUser) {
        if (maxPerUser < 1) {
            throw new IllegalArgumentException("구독 한도(lia.subscriptions.max-per-user)는 1 이상이어야 합니다: " + maxPerUser);
        }
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.maxPerUser = maxPerUser;
    }

    public int maxPerUser() {
        return maxPerUser;
    }

    /** 순서: 계정 잠금 → 이미 구독(시점만 변경, 한도 무관) → 법령 행 없음 → 한도 → 삽입. */
    public SubscribeResult subscribe(UUID userId, String lawId, int notifyBefore) {
        return transactions.execute(tx -> {
            boolean accountExists = jdbc.sql("SELECT 1 FROM accounts WHERE user_id = :userId FOR UPDATE")
                    .param("userId", userId).query(Integer.class).optional().isPresent();
            if (!accountExists) return SubscribeResult.ACCOUNT_NOT_FOUND;
            int updated = jdbc.sql("""
                    UPDATE law_subscriptions SET notify_before = :notifyBefore WHERE user_id = :userId AND law_id = :lawId
                    """)
                    .param("notifyBefore", notifyBefore).param("userId", userId).param("lawId", lawId).update();
            if (updated > 0) return SubscribeResult.UPDATED;
            boolean lawExists = jdbc.sql("SELECT 1 FROM law_versions WHERE law_id = :lawId LIMIT 1")
                    .param("lawId", lawId).query(Integer.class).optional().isPresent();
            if (!lawExists) return SubscribeResult.LAW_NOT_FOUND;
            int count = jdbc.sql("SELECT count(*) FROM law_subscriptions WHERE user_id = :userId")
                    .param("userId", userId).query(Integer.class).single();
            if (count >= maxPerUser) return SubscribeResult.LIMIT;
            jdbc.sql("""
                    INSERT INTO law_subscriptions (user_id, law_id, notify_before, created_at)
                    VALUES (:userId, :lawId, :notifyBefore, now())
                    """)
                    .param("userId", userId).param("lawId", lawId).param("notifyBefore", notifyBefore).update();
            return SubscribeResult.SUBSCRIBED;
        });
    }

    /** 멱등. */
    public void unsubscribe(UUID userId, String lawId) {
        jdbc.sql("DELETE FROM law_subscriptions WHERE user_id = :userId AND law_id = :lawId")
                .param("userId", userId).param("lawId", lawId).update();
    }

    /** 다음 시행일 가까운 순, 없는 것은 뒤(추가 최근 순). 법령명은 가장 이른 시행예정본, 없으면 시행일이 가장 늦은 행. */
    public List<SubscribedLaw> list(UUID userId) {
        return jdbc.sql("""
                SELECT s.law_id, s.notify_before, s.created_at, v.title,
                       (SELECT min(p.effective_date) FROM law_versions p
                        WHERE p.law_id = s.law_id AND p.status = '시행예정') AS next_effective_date
                FROM law_subscriptions s
                JOIN LATERAL (
                    SELECT title FROM law_versions lv
                    WHERE lv.law_id = s.law_id
                    ORDER BY (lv.status = '시행예정') DESC,
                             CASE WHEN lv.status = '시행예정' THEN lv.effective_date END ASC,
                             lv.effective_date DESC
                    LIMIT 1
                ) v ON true
                WHERE s.user_id = :userId
                ORDER BY next_effective_date ASC NULLS LAST, s.created_at DESC, s.law_id
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> new SubscribedLaw(rs.getString("law_id"), rs.getString("title"),
                        rs.getInt("notify_before"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("next_effective_date", LocalDate.class)))
                .list();
    }
}
