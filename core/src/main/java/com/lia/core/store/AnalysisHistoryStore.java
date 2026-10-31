package com.lia.core.store;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 질의·분석 이력(D73) — {@code analysis_history}. 스냅샷은 불투명한 JSON 문자열로만 다룬다(만드는 것은 web).
 * 저장은 한 트랜잭션에서 그 사용자의 {@code accounts} 행을 잠가 같은 사용자의 저장을 직렬화한다(북마크와 같은 방식).
 * "법령 갱신됨" = 저장한 버전의 지금 revision·상태가 저장 당시와 다르거나 행이 없음. 설계: docs/components/web/HistoryApi.md
 */
public class AnalysisHistoryStore {

    public enum SaveStatus { SAVED, ALREADY, LIMIT, LAW_NOT_FOUND, ACCOUNT_NOT_FOUND }

    /** @param id SAVED·ALREADY 면 그 이력 id, 나머지는 null */
    public record SaveResult(SaveStatus status, Long id) {}

    public record NewEntry(String analysisId, String query, String lawId, LocalDate effectiveDate, String lawTitle,
                           String snapshotJson) {}

    public record HistoryRow(long id, String query, String lawId, String lawTitle, LocalDate effectiveDate,
                             OffsetDateTime savedAt, boolean lawUpdated) {}

    /** @param latestEffectiveDate 그 법령의 가장 이른 시행예정본 — 없으면 null */
    public record HistoryEntry(HistoryRow row, String snapshotJson, LocalDate latestEffectiveDate) {}

    private static final String ROW_COLUMNS = """
            h.id, h.query, h.law_id, h.law_title, h.effective_date, h.created_at,
            NOT EXISTS (
                SELECT 1 FROM law_versions v
                WHERE v.law_id = h.law_id AND v.effective_date = h.effective_date
                  AND v.revision = h.law_revision AND v.status = h.law_status
            ) AS law_updated
            """;

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final int maxPerUser;

    public AnalysisHistoryStore(JdbcClient jdbc, TransactionTemplate transactions, int maxPerUser) {
        if (maxPerUser < 1) {
            throw new IllegalArgumentException("이력 한도(lia.history.max-per-user)는 1 이상이어야 합니다: " + maxPerUser);
        }
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.maxPerUser = maxPerUser;
    }

    public int maxPerUser() {
        return maxPerUser;
    }

    /**
     * 순서: 계정 잠금 → 같은 analysis_id 있음(그 기록의 스냅샷만 지금 결과로 바꾸고 ALREADY — 한도와 무관, id·저장 당시
     * 법령 revision·상태·저장 시각은 그대로) → 그 버전 행 없음 → 한도 → 저장 당시 revision·상태와 함께 삽입.
     */
    public SaveResult save(UUID userId, NewEntry entry) {
        return transactions.execute(tx -> {
            boolean accountExists = jdbc.sql("SELECT 1 FROM accounts WHERE user_id = :userId FOR UPDATE")
                    .param("userId", userId).query(Integer.class).optional().isPresent();
            if (!accountExists) return new SaveResult(SaveStatus.ACCOUNT_NOT_FOUND, null);
            Optional<Long> existing = jdbc.sql("SELECT id FROM analysis_history WHERE user_id = :userId AND analysis_id = :analysisId")
                    .param("userId", userId).param("analysisId", entry.analysisId()).query(Long.class).optional();
            if (existing.isPresent()) {
                jdbc.sql("UPDATE analysis_history SET snapshot = CAST(:snapshot AS jsonb) WHERE id = :id")
                        .param("snapshot", entry.snapshotJson()).param("id", existing.get()).update();
                return new SaveResult(SaveStatus.ALREADY, existing.get());
            }
            Optional<String[]> version = jdbc.sql("""
                    SELECT revision, status FROM law_versions WHERE law_id = :lawId AND effective_date = :efYd
                    """)
                    .param("lawId", entry.lawId()).param("efYd", entry.effectiveDate())
                    .query((rs, rowNum) -> new String[] {rs.getString("revision"), rs.getString("status")})
                    .optional();
            if (version.isEmpty()) return new SaveResult(SaveStatus.LAW_NOT_FOUND, null);
            int count = jdbc.sql("SELECT count(*) FROM analysis_history WHERE user_id = :userId")
                    .param("userId", userId).query(Integer.class).single();
            if (count >= maxPerUser) return new SaveResult(SaveStatus.LIMIT, null);
            long id = jdbc.sql("""
                    INSERT INTO analysis_history (user_id, analysis_id, query, law_id, effective_date, law_title,
                                                  law_revision, law_status, snapshot, created_at)
                    VALUES (:userId, :analysisId, :query, :lawId, :efYd, :title, :revision, :status, CAST(:snapshot AS jsonb), now())
                    RETURNING id
                    """)
                    .param("userId", userId).param("analysisId", entry.analysisId()).param("query", entry.query())
                    .param("lawId", entry.lawId()).param("efYd", entry.effectiveDate()).param("title", entry.lawTitle())
                    .param("revision", version.get()[0]).param("status", version.get()[1])
                    .param("snapshot", entry.snapshotJson())
                    .query(Long.class).single();
            return new SaveResult(SaveStatus.SAVED, id);
        });
    }

    /** 저장 최근 순. */
    public List<HistoryRow> list(UUID userId) {
        return jdbc.sql("SELECT " + ROW_COLUMNS + " FROM analysis_history h WHERE h.user_id = :userId ORDER BY h.created_at DESC, h.id DESC")
                .param("userId", userId)
                .query((rs, rowNum) -> row(rs))
                .list();
    }

    /** 남의 기록은 없는 것과 같다. */
    public Optional<HistoryEntry> find(UUID userId, long id) {
        return jdbc.sql("""
                SELECT %s, h.snapshot::text AS snapshot,
                       (SELECT min(v.effective_date) FROM law_versions v
                        WHERE v.law_id = h.law_id AND v.status = '시행예정') AS latest_effective_date
                FROM analysis_history h WHERE h.user_id = :userId AND h.id = :id
                """.formatted(ROW_COLUMNS))
                .param("userId", userId).param("id", id)
                .query((rs, rowNum) -> new HistoryEntry(row(rs), rs.getString("snapshot"),
                        rs.getObject("latest_effective_date", LocalDate.class)))
                .optional();
    }

    /** 멱등 — 없거나 남의 것이면 아무것도 지우지 않는다. */
    public void delete(UUID userId, long id) {
        jdbc.sql("DELETE FROM analysis_history WHERE user_id = :userId AND id = :id")
                .param("userId", userId).param("id", id).update();
    }

    private static HistoryRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HistoryRow(rs.getLong("id"), rs.getString("query"), rs.getString("law_id"), rs.getString("law_title"),
                rs.getObject("effective_date", LocalDate.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getBoolean("law_updated"));
    }
}
