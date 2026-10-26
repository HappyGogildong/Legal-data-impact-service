package com.lia.core.store;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 자주 찾는 법령 집계(D66) — {@code law_views_daily}에 서울 날짜별 횟수만 쌓고, 구간 합산 순위를 낸다.
 * 순위는 시행예정·오늘 이후 버전만, 직전 같은 길이 구간의 순위를 함께 준다. 설계: docs/components/web/TrendingApi.md
 */
@Repository
public class LawViewStore implements LawViews {

    private static final String TODAY_SEOUL = "CAST(now() AT TIME ZONE 'Asia/Seoul' AS date)";

    private final JdbcClient jdbc;

    public LawViewStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean record(String lawId, LocalDate effectiveDate) {
        // 버전이 있을 때만 넣는다(없는 법령이 순위 테이블을 오염시키지 않게) — 한 문장이라 동시 요청에도 잃지 않는다
        int changed = jdbc.sql("""
                INSERT INTO law_views_daily (law_id, effective_date, view_date, views)
                SELECT :lawId, :efYd, %s, 1
                WHERE EXISTS (SELECT 1 FROM law_versions WHERE law_id = :lawId AND effective_date = :efYd)
                ON CONFLICT (law_id, effective_date, view_date) DO UPDATE SET views = law_views_daily.views + 1
                """.formatted(TODAY_SEOUL))
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .update();
        return changed > 0;
    }

    /** 구간 합산 순위(오늘 포함 {@code window.days()}일) + 직전 같은 길이 구간 순위. */
    public TrendingPage trending(TrendingWindow window, int limit) {
        List<TrendingItem> items = jdbc.sql("""
                WITH today AS (SELECT %s AS d),
                eligible AS (
                    SELECT law_id, effective_date, title FROM law_versions, today
                    WHERE status = '시행예정' AND effective_date >= today.d
                ),
                current_window AS (
                    SELECT v.law_id, v.effective_date, sum(v.views) AS views
                    FROM law_views_daily v, today
                    WHERE v.view_date > today.d - :days AND v.view_date <= today.d
                    GROUP BY v.law_id, v.effective_date
                ),
                previous_window AS (
                    SELECT v.law_id, v.effective_date, sum(v.views) AS views
                    FROM law_views_daily v, today
                    WHERE v.view_date > today.d - 2 * :days AND v.view_date <= today.d - :days
                    GROUP BY v.law_id, v.effective_date
                ),
                current_ranked AS (
                    SELECT c.law_id, c.effective_date, e.title, c.views,
                           row_number() OVER (ORDER BY c.views DESC, c.law_id, c.effective_date) AS rank
                    FROM current_window c JOIN eligible e USING (law_id, effective_date)
                ),
                previous_ranked AS (
                    SELECT p.law_id, p.effective_date,
                           row_number() OVER (ORDER BY p.views DESC, p.law_id, p.effective_date) AS rank
                    FROM previous_window p JOIN eligible e USING (law_id, effective_date)
                )
                SELECT c.rank, c.law_id, c.title, c.effective_date, c.views, p.rank AS previous_rank
                FROM current_ranked c LEFT JOIN previous_ranked p USING (law_id, effective_date)
                ORDER BY c.rank
                LIMIT :limit
                """.formatted(TODAY_SEOUL))
                .param("days", window.days())
                .param("limit", limit)
                .query((rs, rowNum) -> {
                    // row_number() 는 bigint — pgjdbc 가 Integer 변환을 거부하므로 Long 으로 읽어 줄인다
                    Long previousRank = rs.getObject("previous_rank", Long.class);
                    return new TrendingItem(
                            rs.getInt("rank"), rs.getString("law_id"), rs.getString("title"),
                            rs.getObject("effective_date", LocalDate.class), rs.getLong("views"),
                            previousRank == null ? null : previousRank.intValue());
                })
                .list();
        return new TrendingPage(window.name(), items);
    }

    /** @param window day · week · month */
    public record TrendingPage(String window, List<TrendingItem> items) {}

    /**
     * @param previousRank 직전 같은 길이 구간의 순위, 그때 순위에 없었으면 null(신규). OpenAPI 3.1 이라
     *                     null 을 타입에 명시해야 웹 생성 타입이 {@code number | null} 이 된다.
     */
    public record TrendingItem(int rank, String lawId, String title, LocalDate effectiveDate,
                               long views,
                               @Schema(types = {"integer", "null"}) Integer previousRank) {}
}
