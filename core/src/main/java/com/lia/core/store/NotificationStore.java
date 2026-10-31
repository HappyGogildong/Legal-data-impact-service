package com.lia.core.store;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.pipeline.batch.DueNotifications;

/**
 * 인앱 알림함(D74) — {@code notifications}. 배치가 {@link #createDue} 로 만들고, 고유 키
 * {@code (user_id, law_id, effective_date, notify_before)} 가 정확히 한 번을 보장한다(이 테이블이 Outbox 역할).
 * 대상은 시행예정 버전과 그날 시행으로 바뀐 버전(같은 실행의 TRANSITION 뒤 시행일 당일 알림용, {@code DUE}).
 * 설계: docs/components/web/SubscriptionApi.md
 */
public class NotificationStore implements DueNotifications {

    public record NotificationRow(long id, String lawId, String title, LocalDate effectiveDate, int notifyBefore,
                                  OffsetDateTime createdAt, boolean read) {}

    /**
     * 구독 × 시행예정 버전 중 알릴 때가 된 것 — createDue·countDue 가 같은 조건을 쓴다.
     * 오늘 시행된 버전은 {@code 시행중} 이어도 포함한다 — run 이 같은 실행에서 TRANSITION 으로 먼저 바꾸므로,
     * 시행일 당일 알림(당일 시점·전날을 놓친 보충)이 빠지지 않게. 기준선(옛 시행중본)은 시행일이 과거라 들어오지 않는다.
     */
    private static final String DUE = """
            FROM law_subscriptions s
            JOIN law_versions v ON v.law_id = s.law_id
             AND (v.status = '시행예정' OR (v.status = '시행중' AND v.effective_date = :today))
            WHERE v.effective_date - s.notify_before <= :today AND :today <= v.effective_date
            """;

    private final JdbcClient jdbc;

    public NotificationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int createDue(LocalDate today) {
        return jdbc.sql("""
                INSERT INTO notifications (user_id, law_id, effective_date, law_title, notify_before, created_at)
                SELECT s.user_id, v.law_id, v.effective_date, v.title, s.notify_before, now()
                """ + DUE + """
                ON CONFLICT (user_id, law_id, effective_date, notify_before) DO NOTHING
                """)
                .param("today", today)
                .update();
    }

    @Override
    public int countDue(LocalDate today) {
        return jdbc.sql("SELECT count(*) " + DUE + """
                  AND NOT EXISTS (
                      SELECT 1 FROM notifications n
                      WHERE n.user_id = s.user_id AND n.law_id = v.law_id
                        AND n.effective_date = v.effective_date AND n.notify_before = s.notify_before
                  )
                """)
                .param("today", today)
                .query(Integer.class).single();
    }

    /** 최근 순 {@code limit} 개. */
    public List<NotificationRow> list(UUID userId, int limit) {
        return jdbc.sql("""
                SELECT id, law_id, law_title, effective_date, notify_before, created_at, read_at IS NOT NULL AS is_read
                FROM notifications WHERE user_id = :userId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """)
                .param("userId", userId).param("limit", limit)
                .query((rs, rowNum) -> new NotificationRow(rs.getLong("id"), rs.getString("law_id"),
                        rs.getString("law_title"), rs.getObject("effective_date", LocalDate.class),
                        rs.getInt("notify_before"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getBoolean("is_read")))
                .list();
    }

    public int unreadCount(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL")
                .param("userId", userId).query(Integer.class).single();
    }

    /** 멱등 — 남의 알림이면 아무것도 바꾸지 않는다. */
    public void markRead(UUID userId, long id) {
        jdbc.sql("UPDATE notifications SET read_at = now() WHERE user_id = :userId AND id = :id AND read_at IS NULL")
                .param("userId", userId).param("id", id).update();
    }

    public void markAllRead(UUID userId) {
        jdbc.sql("UPDATE notifications SET read_at = now() WHERE user_id = :userId AND read_at IS NULL")
                .param("userId", userId).update();
    }
}
