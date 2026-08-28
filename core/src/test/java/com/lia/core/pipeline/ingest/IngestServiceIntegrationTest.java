package com.lia.core.pipeline.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.LawStore;

/**
 * 적재 파이프라인 조립 통합 테스트 — 실 Postgres(Testcontainers)에
 * {@code Normalizer → DiffBuilder → LawStore} 가 엮여 도는지 검증. API 없이 fixture 로.
 * {@code @SpringBootTest} 아님(다른 컨텍스트 무영향). Docker 없으면 자동 스킵.
 */
@Testcontainers(disabledWithoutDocker = true)
class IngestServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private static final ObjectMapper JSON = new ObjectMapper();
    static JdbcClient jdbc;
    static LawStore store;
    static IngestService ingest;

    @BeforeAll
    static void setup() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = JdbcClient.create(ds);
        store = new LawStore(jdbc, JSON);
        ingest = new IngestService(null, new Normalizer(), new DiffBuilder(), store, null, null);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM law_versions").update();
    }

    @Test
    @DisplayName("pending + baseline → normalize·diff·저장. 시행예정본+시행중본 둘 다 upsert")
    void 조립_기준선_있음() {
        RawLaw pending = raw("samples/housing-act.json", "시행예정");
        RawLaw baseline = raw("samples/housing-act-baseline.json", "시행중");

        IngestService.IngestResult r = ingest.store(pending, baseline);

        assertEquals("001809", r.lawId());
        assertEquals(LocalDate.of(2026, 8, 4), r.effectiveDate());
        assertEquals(3, r.changedCount(), "변경 조문 3개(18·104·이동57)");
        assertTrue(r.hasBaseline());

        // 시행예정 정본 저장 + diff 반영
        Law stored = store.find("001809", LocalDate.of(2026, 8, 4)).orElseThrow();
        assertEquals(ChangeType.개정, stored.article("18").changeType());
        assertTrue(stored.article("18").diffVsCurrent().contains("현행"), "diff 근거 저장됨");

        // 시행중 정본 = diff 기준선도 저장됨 → findBaseline 로 조회
        Law base = store.findBaseline("001809").orElseThrow();
        assertEquals(Law.Status.시행중, base.status());
        assertEquals(LocalDate.of(2020, 1, 1), base.effectiveDate());
    }

    @Test
    @DisplayName("제정(기준선 null) → 변경 조문 전부 신설, findBaseline empty")
    void 조립_제정_기준선_없음() {
        RawLaw pending = raw("samples/housing-act.json", "시행예정");

        IngestService.IngestResult r = ingest.store(pending, null);

        assertEquals(3, r.changedCount());
        assertFalse(r.hasBaseline());

        Law stored = store.find("001809", LocalDate.of(2026, 8, 4)).orElseThrow();
        assertEquals(ChangeType.신설, stored.article("18").changeType(), "기준선 없으면 전부 신설");
        assertTrue(store.findBaseline("001809").isEmpty(), "시행중본을 저장하지 않았으므로 기준선 없음");
    }

    // --- fixture 로더 -----------------------------------------------------

    private static RawLaw raw(String path, String status) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JSON.readValue(body, LinkedHashMap.class);
            return new RawLaw(null, null, null, status, null, null, null, root);
        } catch (Exception e) {
            throw new RuntimeException("fixture 로드 실패: " + path, e);
        }
    }
}
