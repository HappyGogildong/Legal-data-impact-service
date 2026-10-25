package com.lia.core.store;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lia.core.domain.law.Law;

/**
 * 법령 목록·시행예정본 목록 — {@code law_versions} 읽기 전용(홈 곧 시행·S06·S05 시행일 선택, D65).
 * 목록은 컬럼과 {@code payload->>} 필드만 읽는다(payload 전체를 역직렬화하지 않는다). 상세는 {@link LawSource}.
 * 오늘 = 서울 날짜({@code now() AT TIME ZONE 'Asia/Seoul'}). 설계: docs/components/web/LawCatalogApi.md
 */
@Repository
public class LawCatalogStore {

    private static final String TODAY_SEOUL = "CAST(now() AT TIME ZONE 'Asia/Seoul' AS date)";

    private final JdbcClient jdbc;

    public LawCatalogStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 오늘 이후 시행예정본 — 시행일·lawId 오름차순, {@code after} 다음부터 {@code limit}개. from·to·after 는 null 이면 조건 없음. */
    public LawListPage listPending(LocalDate from, LocalDate to, Cursor after, int limit) {
        List<LawListItem> rows = jdbc.sql("""
                SELECT law_id, title, effective_date,
                       payload->>'lawType' AS law_type, payload->>'ministry' AS ministry,
                       payload->>'amendKind' AS amend_kind
                FROM law_versions
                WHERE status = '시행예정'
                  AND effective_date >= %s
                  AND (CAST(:from AS date) IS NULL OR effective_date >= CAST(:from AS date))
                  AND (CAST(:to AS date) IS NULL OR effective_date <= CAST(:to AS date))
                  AND (CAST(:afterDate AS date) IS NULL
                       OR (effective_date, law_id) > (CAST(:afterDate AS date), CAST(:afterLawId AS text)))
                ORDER BY effective_date, law_id
                LIMIT :fetch
                """.formatted(TODAY_SEOUL))
                .param("from", from, Types.DATE)
                .param("to", to, Types.DATE)
                .param("afterDate", after == null ? null : after.effectiveDate(), Types.DATE)
                .param("afterLawId", after == null ? null : after.lawId(), Types.VARCHAR)
                .param("fetch", limit + 1)   // 한 행 더 읽어 다음 쪽이 있는지 안다
                .query((rs, rowNum) -> new LawListItem(
                        rs.getString("law_id"), rs.getString("title"), rs.getObject("effective_date", LocalDate.class),
                        rs.getString("law_type"), rs.getString("ministry"), rs.getString("amend_kind")))
                .list();
        if (rows.size() <= limit) return new LawListPage(rows, null);
        List<LawListItem> page = rows.subList(0, limit);
        LawListItem last = page.getLast();
        return new LawListPage(List.copyOf(page), new Cursor(last.effectiveDate(), last.lawId()).encode());
    }

    /** 한 법령의 시행예정본(오늘 이후, 시행일 순)과 기준선(가장 최근 시행중본). 버전이 하나도 없으면 empty. */
    public Optional<LawVersions> versions(String lawId) {
        List<VersionRow> rows = jdbc.sql("""
                SELECT title, effective_date, status,
                       payload->>'promulgateNo' AS promulgate_no, payload->>'amendKind' AS amend_kind,
                       effective_date >= %s AS upcoming
                FROM law_versions
                WHERE law_id = :lawId
                ORDER BY effective_date
                """.formatted(TODAY_SEOUL))
                .param("lawId", lawId)
                .query((rs, rowNum) -> new VersionRow(
                        rs.getString("title"), rs.getObject("effective_date", LocalDate.class), rs.getString("status"),
                        rs.getString("promulgate_no"), rs.getString("amend_kind"), rs.getBoolean("upcoming")))
                .list();
        if (rows.isEmpty()) return Optional.empty();
        List<UpcomingVersion> pending = rows.stream()
                .filter(row -> row.upcoming() && Law.Status.시행예정.name().equals(row.status()))
                .map(row -> new UpcomingVersion(row.effectiveDate(), row.promulgateNo(), row.amendKind()))
                .toList();
        InForceVersion current = rows.stream()
                .filter(row -> Law.Status.시행중.name().equals(row.status()))
                .reduce((earlier, later) -> later)
                .map(row -> new InForceVersion(row.effectiveDate(), row.promulgateNo()))
                .orElse(null);
        return Optional.of(new LawVersions(lawId, rows.getLast().title(), pending, current));
    }

    private record VersionRow(String title, LocalDate effectiveDate, String status,
                              String promulgateNo, String amendKind, boolean upcoming) {}

    /** 키셋 커서 — 마지막 행의 (시행일, lawId). 바깥에는 base64url 불투명 문자열로만 나간다. */
    public record Cursor(LocalDate effectiveDate, String lawId) {

        public String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((effectiveDate + "|" + lawId).getBytes(StandardCharsets.UTF_8));
        }

        /** 잘못된 커서는 {@link IllegalArgumentException}(→ 400). */
        public static Cursor decode(String value) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                int separator = raw.indexOf('|');
                if (separator < 0 || separator == raw.length() - 1) {
                    throw new IllegalArgumentException("구분자 또는 lawId 없음");
                }
                return new Cursor(LocalDate.parse(raw.substring(0, separator)), raw.substring(separator + 1));
            } catch (IllegalArgumentException | DateTimeException e) {
                throw new IllegalArgumentException("잘못된 cursor 입니다.", e);
            }
        }
    }

    /** @param nextCursor 다음 쪽 커서, 없으면 null */
    public record LawListPage(List<LawListItem> items, String nextCursor) {}

    public record LawListItem(String lawId, String title, LocalDate effectiveDate,
                              String lawType, String ministry, String amendKind) {}

    /** @param current 시행중본(diff 기준선), 없으면 null */
    public record LawVersions(String lawId, String title, List<UpcomingVersion> pending, InForceVersion current) {}

    public record UpcomingVersion(LocalDate effectiveDate, String promulgateNo, String amendKind) {}

    public record InForceVersion(LocalDate effectiveDate, String promulgateNo) {}
}
