package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lia.core.testsupport.FailingEmbeddingModel;
import com.lia.core.testsupport.FakeEmbeddingModel;

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
    static DataSource ds;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static ChunkStore store;

    @BeforeAll
    static void setup() {
        ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        store = new PgVectorChunkStore(vectorStore(new FakeEmbeddingModel(DIM)), tx);
    }

    /** 같은 DataSource 위의 PgVectorStore — 트랜잭션 매니저와 커넥션을 공유해야 교체가 한 트랜잭션이 된다. */
    private static PgVectorStore vectorStore(EmbeddingModel embeddingModel) {
        PgVectorStore vectorStore = PgVectorStore.builder(jdbc, embeddingModel)
                .dimensions(DIM)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
        vectorStore.afterPropertiesSet();       // 스키마·확장·인덱스 생성(이미 있으면 그대로)
        return vectorStore;
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

    @Test
    @DisplayName("교체 중 임베딩이 실패하면 롤백 — 이전 청크가 그대로 남는다")
    void 교체실패_롤백() {
        store.replaceVersion(LAW, EF, List.of(chunk(LAW, EF, "LAW:001809@2026-08-04:art:18", "old")));
        ChunkStore failing = new PgVectorChunkStore(vectorStore(new FailingEmbeddingModel(DIM)), tx);

        assertThrows(RuntimeException.class, () -> failing.replaceVersion(LAW, EF,
                List.of(chunk(LAW, EF, "LAW:001809@2026-08-04:art:18", "new"))));

        assertEquals(1, rowsByLaw(LAW, EF), "삭제가 롤백돼 이전 청크가 남아야 한다");
    }

    @Test
    @DisplayName("빈 목록으로 교체하면 그 정본의 색인이 제거된다(시행 전환·철회)")
    void 빈목록_색인제거() {
        store.replaceVersion(LAW, EF, List.of(chunk(LAW, EF, "LAW:001809@2026-08-04:art:18", "A")));

        store.replaceVersion(LAW, EF, List.of());

        assertEquals(0, rowsByLaw(LAW, EF));
    }

    @Test
    @DisplayName("유사도 하한 아래 청크는 버린다 — 같은 텍스트만 남는다")
    void 유사도_하한() {
        store.replaceVersion(LAW, EF, List.of(
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:18", "통합심의 절차를 통합하여 검토한다"),
                chunk(LAW, EF, "LAW:001809@2026-08-04:art:104", "벌칙 2년 이하의 징역")));

        List<Chunk> all = store.search("통합심의 절차를 통합하여 검토한다", 5, 0.0);
        List<Chunk> strict = store.search("통합심의 절차를 통합하여 검토한다", 5, 0.999);

        assertEquals(2, all.size(), "하한 0 이면 전부");
        assertEquals(List.of("LAW:001809@2026-08-04:art:18"), strict.stream().map(Chunk::sourceId).toList());
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

}
