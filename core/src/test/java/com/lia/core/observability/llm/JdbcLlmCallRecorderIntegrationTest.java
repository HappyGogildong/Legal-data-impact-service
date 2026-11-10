package com.lia.core.observability.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.testsupport.TestDatabases;

/** llm_calls 쓰기와 llm_call_costs 의 시점별 단가 계산(V16). Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class JdbcLlmCallRecorderIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcClient jdbc;

    private static final String OPUS = "claude-opus-5-5";
    /** 2026-11-10 12:00 KST. */
    private static final Instant NOV_10 = Instant.parse("2026-11-10T03:00:00Z");

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM llm_calls").update();
        jdbc.sql("DELETE FROM llm_prices").update();
    }

    private static void price(String kind, String usdPerMillion, String effectiveFrom) {
        jdbc.sql("INSERT INTO llm_prices (model, token_kind, usd_per_million, effective_from) VALUES (?, ?, ?, ?::date)")
                .params(OPUS, kind, new BigDecimal(usdPerMillion), effectiveFrom).update();
    }

    private static LlmCall opusCall(Instant at, LlmUsage usage) {
        return new LlmCall(at, LlmPurpose.PERSONALIZED, OPUS, "0.3", UUID.randomUUID(), 2, true, true,
                "IMPACT,ACTION", usage, 12);
    }

    @Test
    void 기록한_값을_그대로_남긴다() {
        UUID group = UUID.randomUUID();
        new JdbcLlmCallRecorder(jdbc).record(new LlmCall(NOV_10, LlmPurpose.PERSONALIZED, OPUS, "0.3", group, 2, false,
                true, "IMPACT,ACTION", new LlmUsage(OPUS, 1000, 200, 50, 10, "max_tokens"), 12));

        Map<String, Object> row = jdbc.sql("SELECT * FROM llm_calls").query().singleRow();
        assertEquals("PERSONALIZED", row.get("purpose"));
        assertEquals(OPUS, row.get("model"));
        assertEquals("0.3", row.get("prompt_version"));
        assertEquals(group, row.get("group_key"));
        assertEquals(2, ((Number) row.get("attempt")).intValue());
        assertEquals(false, row.get("accepted"));
        assertEquals(true, row.get("succeeded"));
        assertEquals("IMPACT,ACTION", row.get("requested"));
        assertEquals(1000, row.get("input_tokens"));
        assertEquals(200, row.get("output_tokens"));
        assertEquals(50, row.get("cache_read_tokens"));
        assertEquals(10, row.get("cache_write_tokens"));
        assertEquals("max_tokens", row.get("stop_reason"));
        assertEquals(12, ((Number) row.get("context_sources")).intValue());
    }

    @Test
    void 비어_있는_값은_NULL로_남긴다() {
        new JdbcLlmCallRecorder(jdbc).record(LlmCall.single(NOV_10, LlmPurpose.CLASSIFY, "claude-haiku-4-5-20251001",
                false, LlmUsage.NONE));

        Map<String, Object> row = jdbc.sql("SELECT * FROM llm_calls").query().singleRow();
        assertNull(row.get("group_key"));
        assertNull(row.get("accepted"));
        assertNull(row.get("input_tokens"));
        assertNull(row.get("context_sources"));
        assertEquals(1, ((Number) row.get("attempt")).intValue());
    }

    @Test
    void 비용은_호출일에_유효한_단가로_계산한다() {
        price("input", "10", "2026-01-01");
        price("output", "50", "2026-01-01");
        price("input", "5", "2026-11-11");      // 호출 다음 날부터 — 적용 안 됨
        new JdbcLlmCallRecorder(jdbc).record(opusCall(NOV_10, new LlmUsage(OPUS, 1_000_000, 100_000, null, null, null)));

        Map<String, Object> row = jdbc.sql("SELECT priced, cost_usd FROM llm_call_costs").query().singleRow();
        assertEquals(true, row.get("priced"));
        assertEquals(0, new BigDecimal("15").compareTo((BigDecimal) row.get("cost_usd")), "10 + 0.1×50");
    }

    @Test
    void 단가가_없는_토큰_종류가_있으면_비용을_비운다() {
        price("input", "10", "2026-01-01");
        new JdbcLlmCallRecorder(jdbc).record(opusCall(NOV_10, new LlmUsage(OPUS, 1000, 10, null, null, null)));

        Map<String, Object> row = jdbc.sql("SELECT priced, cost_usd FROM llm_call_costs").query().singleRow();
        assertEquals(false, row.get("priced"));
        assertNull(row.get("cost_usd"), "0으로 꾸미지 않는다");
    }

    @Test
    void 쓰지_않은_캐시_토큰은_단가가_없어도_된다() {
        price("input", "10", "2026-01-01");
        price("output", "50", "2026-01-01");
        new JdbcLlmCallRecorder(jdbc).record(opusCall(NOV_10, new LlmUsage(OPUS, 1000, 10, 0, 0, null)));

        assertEquals(true, jdbc.sql("SELECT priced FROM llm_call_costs").query(Boolean.class).single());
    }
}
