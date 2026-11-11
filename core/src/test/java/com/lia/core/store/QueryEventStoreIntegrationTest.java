package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.application.analysis.QueryEvent;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.testsupport.TestDatabases;

/** query_events(V17) 쓰기 — 차원 목록 순서·못 채운 사유 JSON. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class QueryEventStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    private static final Instant AT = Instant.parse("2026-11-11T01:00:00Z");

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM query_events").update();
    }

    @Test
    void 결과의_모양을_그대로_남긴다() {
        new QueryEventStore(jdbc).record(new QueryEvent(AT, QueryEvent.Kind.ANALYZE, QueryEvent.Status.ANSWERED, true,
                QueryEvent.Route.REFERENCE, null, QueryType.ACTION, Set.of(QueryType.ACTION, QueryType.IMPACT, QueryType.SUMMARY),
                Set.of(QueryType.IMPACT, QueryType.SUMMARY),
                Map.of(QueryType.ACTION, Unmet.Reason.DAILY_LIMIT), true, null, false, "0.3"));

        Map<String, Object> row = jdbc.sql("""
                SELECT kind, status, signed_in, route, resolution_state, primary_type, types, filled,
                       unmet->>'ACTION' AS unmet_action, article_named, candidates, profile_search, prompt_version
                FROM query_events""").query().singleRow();
        assertEquals("ANALYZE", row.get("kind"));
        assertEquals("ANSWERED", row.get("status"));
        assertEquals(true, row.get("signed_in"));
        assertEquals("REFERENCE", row.get("route"));
        assertNull(row.get("resolution_state"));
        assertEquals("ACTION", row.get("primary_type"));
        assertEquals("SUMMARY,IMPACT,ACTION", row.get("types"), "QueryType 선언 순서");
        assertEquals("SUMMARY,IMPACT", row.get("filled"));
        assertEquals("DAILY_LIMIT", row.get("unmet_action"));
        assertEquals(true, row.get("article_named"));
        assertNull(row.get("candidates"));
        assertEquals(false, row.get("profile_search"));
        assertEquals("0.3", row.get("prompt_version"));
    }

    @Test
    void 결과_모양이_없는_요청은_빈_값으로() {
        new QueryEventStore(jdbc).record(new QueryEvent(AT, QueryEvent.Kind.ANALYZE, QueryEvent.Status.ANONYMOUS_LIMIT,
                false, null, null, null, null, null, null, null, null, true, "0.3"));

        Map<String, Object> row = jdbc.sql("SELECT route, primary_type, types, filled, unmet::text AS unmet FROM query_events")
                .query().singleRow();
        assertNull(row.get("route"));
        assertNull(row.get("primary_type"));
        assertNull(row.get("types"));
        assertNull(row.get("filled"));
        assertEquals("{}", row.get("unmet"));
    }

    @Test
    void 미해소_사유와_후보_수() {
        new QueryEventStore(jdbc).record(new QueryEvent(AT, QueryEvent.Kind.ANALYZE, QueryEvent.Status.UNRESOLVED, false,
                null, "AMBIGUOUS", null, null, null, null, null, 2, false, "0.3"));

        Map<String, Object> row = jdbc.sql("SELECT resolution_state, candidates FROM query_events").query().singleRow();
        assertEquals("AMBIGUOUS", row.get("resolution_state"));
        assertEquals(2, ((Number) row.get("candidates")).intValue());
    }
}
