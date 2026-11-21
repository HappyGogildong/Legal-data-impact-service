package com.lia.core.store;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.resolve.LawLookup;

/**
 * 해소용 법령명 조회 — 적재된 {@code law_versions} 의 시행예정본 제목에서 찾는다(아키텍처 v0.8 §3.2, D87).
 *
 * <p>제목에 검색어가 글자 그대로 들어 있는 오늘(서울) 이후 시행예정본을, 정확히 같은 제목 → 짧은 제목 →
 * 시행일 → lawId 순으로 {@code limit}개. 본문 없는 경량 후보다({@code raw = null}) — 본문은 해소 뒤
 * {@link LawSource} 가 읽는다. 설계: docs/components/resolve/SourceAnalyzer.md
 */
@Repository
public class LawStoreLookup implements LawLookup {

    private static final String TODAY_SEOUL = "CAST(now() AT TIME ZONE 'Asia/Seoul' AS date)";

    private final JdbcClient jdbc;

    public LawStoreLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RawLaw> searchByName(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return List.of();
        return jdbc.sql("""
                SELECT law_id, title, status, effective_date,
                       payload->>'mst' AS mst,
                       CAST(payload->>'promulgateDate' AS date) AS promulgate_date,
                       payload->>'promulgateNo' AS promulgate_no
                FROM law_versions
                WHERE status = '시행예정'
                  AND effective_date >= %s
                  AND strpos(title, :name) > 0
                ORDER BY (title = :name) DESC, length(title), effective_date, law_id
                LIMIT :limit
                """.formatted(TODAY_SEOUL))
                .param("name", query.strip())
                .param("limit", limit)
                .query((rs, rowNum) -> new RawLaw(
                        rs.getString("law_id"), rs.getString("mst"), rs.getString("title"), rs.getString("status"),
                        rs.getObject("effective_date", LocalDate.class),
                        rs.getObject("promulgate_date", LocalDate.class),
                        rs.getString("promulgate_no"), null))
                .list();
    }
}
