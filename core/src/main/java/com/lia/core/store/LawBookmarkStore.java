package com.lia.core.store;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.lia.core.domain.law.Law;

/**
 * 관심 법령 북마크(D72) — {@code law_bookmarks}, 법령(lawId) 단위.
 * 추가는 한 트랜잭션에서 그 사용자의 {@code accounts} 행을 잠가 같은 사용자의 추가를 직렬화한다 — 동시 요청이 한도를 넘지 않는다.
 * 목록은 법령마다 "보여 줄 버전"(가장 이른 시행예정본, 없으면 시행일이 가장 늦은 행)을 붙인다. 설계: docs/components/web/BookmarkApi.md
 */
public class LawBookmarkStore {

    public enum AddResult { ADDED, ALREADY, LIMIT, LAW_NOT_FOUND, ACCOUNT_NOT_FOUND }

    /** @param status 보여 줄 버전의 저장 상태 — 화면 라벨(시행됨 등)로 접는 것은 web 의 일 */
    public record BookmarkedLaw(String lawId, String title, Law.Status status, LocalDate effectiveDate,
                                OffsetDateTime bookmarkedAt) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final int maxPerUser;

    public LawBookmarkStore(JdbcClient jdbc, TransactionTemplate transactions, int maxPerUser) {
        if (maxPerUser < 1) {
            throw new IllegalArgumentException("북마크 한도(lia.bookmarks.max-per-user)는 1 이상이어야 합니다: " + maxPerUser);
        }
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.maxPerUser = maxPerUser;
    }

    public int maxPerUser() {
        return maxPerUser;
    }

    /** 이미 있으면 한도와 무관하게 ALREADY(멱등). 순서: 계정 잠금 → 이미 있음 → 법령 존재 → 한도 → 삽입. */
    public AddResult add(UUID userId, String lawId) {
        return transactions.execute(status -> {
            boolean accountExists = jdbc.sql("SELECT 1 FROM accounts WHERE user_id = :userId FOR UPDATE")
                    .param("userId", userId).query(Integer.class).optional().isPresent();
            if (!accountExists) return AddResult.ACCOUNT_NOT_FOUND;
            boolean already = jdbc.sql("SELECT 1 FROM law_bookmarks WHERE user_id = :userId AND law_id = :lawId")
                    .param("userId", userId).param("lawId", lawId).query(Integer.class).optional().isPresent();
            if (already) return AddResult.ALREADY;
            boolean lawExists = jdbc.sql("SELECT 1 FROM law_versions WHERE law_id = :lawId LIMIT 1")
                    .param("lawId", lawId).query(Integer.class).optional().isPresent();
            if (!lawExists) return AddResult.LAW_NOT_FOUND;
            int count = jdbc.sql("SELECT count(*) FROM law_bookmarks WHERE user_id = :userId")
                    .param("userId", userId).query(Integer.class).single();
            if (count >= maxPerUser) return AddResult.LIMIT;
            jdbc.sql("INSERT INTO law_bookmarks (user_id, law_id, created_at) VALUES (:userId, :lawId, now())")
                    .param("userId", userId).param("lawId", lawId).update();
            return AddResult.ADDED;
        });
    }

    /** 멱등 — 없어도 그대로. */
    public void remove(UUID userId, String lawId) {
        jdbc.sql("DELETE FROM law_bookmarks WHERE user_id = :userId AND law_id = :lawId")
                .param("userId", userId).param("lawId", lawId).update();
    }

    /** 추가한 시각 최근 순. 법령 행이 모두 사라진 북마크는 빠진다(배치가 행을 보존하므로 정상 경로에선 없음). */
    public List<BookmarkedLaw> list(UUID userId) {
        return jdbc.sql("""
                SELECT b.law_id, v.title, v.status, v.effective_date, b.created_at
                FROM law_bookmarks b
                JOIN LATERAL (
                    SELECT title, status, effective_date FROM law_versions lv
                    WHERE lv.law_id = b.law_id
                    ORDER BY (lv.status = '시행예정') DESC,
                             CASE WHEN lv.status = '시행예정' THEN lv.effective_date END ASC,
                             lv.effective_date DESC
                    LIMIT 1
                ) v ON true
                WHERE b.user_id = :userId
                ORDER BY b.created_at DESC, b.law_id
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> new BookmarkedLaw(
                        rs.getString("law_id"), rs.getString("title"), Law.Status.valueOf(rs.getString("status")),
                        rs.getObject("effective_date", LocalDate.class),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }
}
