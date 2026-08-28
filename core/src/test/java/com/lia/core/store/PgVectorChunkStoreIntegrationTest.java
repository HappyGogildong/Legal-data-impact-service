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

    @Test
    @DisplayName("upsert 후 같은 텍스트로 검색하면 그 청크가 top-1")
    void upsert_search_라운드트립() {
        store.upsert(List.of(
                chunk("LAW:001809@2026-08-04:art:18", "통합심의 절차를 통합하여 검토한다"),
                chunk("LAW:001809@2026-08-04:art:104", "벌칙 2년 이하의 징역"),
                chunk("LAW:001809@2026-08-04", "심의 효율화 개정이유")));

        List<Chunk> hits = store.search("통합심의 절차를 통합하여 검토한다", 3);

        assertFalse(hits.isEmpty(), "검색 결과 없음");
        assertEquals("LAW:001809@2026-08-04:art:18", hits.get(0).sourceId(), "동일 텍스트가 top-1이어야");
        assertEquals("article", hits.get(0).metadata().get("kind"), "metadata 왕복 보존");
    }

    @Test
    @DisplayName("같은 source_id 재upsert는 멱등 — 중복 행 없음")
    void upsert_멱등() {
        Chunk c = chunk("LAW:001809@2026-08-04:art:18", "통합심의 본문");
        store.upsert(List.of(c));
        store.upsert(List.of(c));          // 재색인

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'source_id' = ?",
                Integer.class, "LAW:001809@2026-08-04:art:18");
        assertEquals(1, rows, "재upsert 후에도 행은 1개(삭제-후-삽입)");
    }

    private static Chunk chunk(String sourceId, String content) {
        return new Chunk(sourceId, content, Map.of("kind", "article", "namespace", "pending"));
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
