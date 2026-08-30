package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PgVectorChunkStore 통합 — 실 pgvector(Testcontainers)에 upsert→search 라운드트립·멱등 검증.
 * 임베딩은 <b>결정론 Fake EmbeddingModel</b>을 PgVectorStore에 주입해 실 API·비용 없이 재현.
 * Docker 없으면 자동 스킵.
 */
@Testcontainers(disabledWithoutDocker = true)
class PgVectorChunkStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final int DIM = 8;
    static JdbcTemplate jdbc;
    static ChunkStore store;

    @BeforeAll
    static void setup() {
        DataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        PgVectorStore vectorStore = PgVectorStore.builder(jdbc, new FakeEmbeddingModel(DIM))
                .dimensions(DIM)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
        vectorStore.afterPropertiesSet();       // 스키마·확장·인덱스 생성
        store = new PgVectorChunkStore(vectorStore);
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE vector_store");
    }

    private static final String LAW = "001809";
    private static final String EF = "2026-08-04";

    @Test
    @DisplayName("replaceVersion 후 같은 텍스트로 검색하면 그 청크가 top-1")
    void replaceVersion_search_라운드트립() {
        store.replaceVersion(LAW, EF, List.of(
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:18", "통합심의 절차를 통합하여 검토한다"),
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:104", "벌칙 2년 이하의 징역"),
                chunk(LAW, EF, "LAW:001809@2026-08-04", "심의 효율화 개정이유")));

        List<Chunk> hits = store.search("통합심의 절차를 통합하여 검토한다", 3);

        assertFalse(hits.isEmpty(), "검색 결과 없음");
        assertEquals("LAW:001809@2026-08-04:art:18", hits.get(0).sourceId(), "동일 텍스트가 top-1이어야");
        assertEquals("article", hits.get(0).metadata().get("kind"), "metadata 왕복 보존");
    }

    @Test
    @DisplayName("재색인 시 사라진 청크(stale)는 제거된다 — 저장 상태 == 현재 상태")
    void 재색인_stale_제거() {
        // 처음: 과대 조문이 3개로 분할됨
        store.replaceVersion(LAW, EF, List.of(
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:18#0", "part0"),
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:18#1", "part1"),
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:18#2", "part2")));
        // 재색인: 내용이 줄어 2개로 — #2는 이제 없다
        store.replaceVersion(LAW, EF, List.of(
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:18#0", "part0"),
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:18#1", "part1")));

        assertEquals(0, rowsBySource("LAW:001809@2026-08-04:art:18#2"), "사라진 하위 청크가 남았다(stale)");
        assertEquals(2, rowsByLaw(LAW, EF), "정본의 청크가 현재 세트(2개)와 정확히 일치해야");
    }

    @Test
    @DisplayName("replaceVersion은 다른 정본을 건드리지 않는다 — 스코프 격리")
    void 다른_정본은_보존() {
        store.replaceVersion(LAW, EF, List.of(chunk(LAW, EF, "LAW:001809@2026-08-04:art:18", "A")));
        store.replaceVersion("002000", "2026-09-01",
                List.of(chunk("002000", "2026-09-01", "LAW:002000@2026-09-01:art:5", "B")));

        // 001809 정본만 재색인 — 002000 은 그대로여야
        store.replaceVersion(LAW, EF, List.of(chunk(LAW, EF, "LAW:001809@2026-08-04:art:18", "A2")));

        assertEquals(1, rowsByLaw("002000", "2026-09-01"), "다른 정본이 삭제됐다 — 스코프가 새고 있다");
        assertEquals(1, rowsByLaw(LAW, EF), "재색인된 정본은 1개");
    }

    private Integer rowsBySource(String sourceId) {
        return jdbc.queryForObject("SELECT count(*) FROM vector_store WHERE metadata->>'source_id' = ?",
                Integer.class, sourceId);
    }

    private Integer rowsByLaw(String lawId, String efYd) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'lawId' = ? AND metadata->>'efYd' = ?",
                Integer.class, lawId, efYd);
    }

    private static Chunk chunk(String lawId, String efYd, String sourceId, String content) {
        return new Chunk(sourceId, content,
                Map.of("kind", "article", "namespace", "pending", "lawId", lawId, "efYd", efYd));
    }

    /** 텍스트 해시로 시드한 결정론 벡터 — 같은 텍스트는 같은 벡터. 실 API·비용 없음. */
    static class FakeEmbeddingModel implements EmbeddingModel {
        private final int dim;
        FakeEmbeddingModel(int dim) { this.dim = dim; }

        @Override public float[] embed(Document document) { return vec(document.getText()); }

        @Override public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> texts = request.getInstructions();
            List<Embedding> out = new java.util.ArrayList<>();
            for (int i = 0; i < texts.size(); i++) out.add(new Embedding(vec(texts.get(i)), i));
            return new EmbeddingResponse(out);
        }

        @Override public int dimensions() { return dim; }

        private float[] vec(String s) {
            Random rnd = new Random(s.hashCode());
            float[] v = new float[dim];
            double sum = 0;
            for (int i = 0; i < dim; i++) { v[i] = (float) (rnd.nextDouble() * 2 - 1); sum += v[i] * (double) v[i]; }
            double norm = Math.sqrt(sum);
            for (int i = 0; i < dim; i++) v[i] = (float) (v[i] / norm);
            return v;
        }
    }
}
