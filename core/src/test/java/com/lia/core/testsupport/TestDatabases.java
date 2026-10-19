package com.lia.core.testsupport;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import com.lia.core.store.PgVectorChunkStore;

/** Testcontainers DB 준비 — Flyway 전체 마이그레이션과 가짜 임베딩 위의 ChunkStore. */
public final class TestDatabases {

    private TestDatabases() {}

    /** 모든 마이그레이션을 적용하고 그 DB 의 DataSource 를 돌려준다. 트랜잭션을 쓰는 테스트는 이 인스턴스 하나를 공유한다. */
    public static DataSource migrate(PostgreSQLContainer<?> pg) {
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        return new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    /** 같은 DataSource 위의 PgVectorStore(+트랜잭션) — vector_store 테이블도 여기서 만든다. */
    public static PgVectorChunkStore chunkStore(DataSource ds, EmbeddingModel embeddingModel, int dim) {
        PgVectorStore vectorStore = PgVectorStore.builder(new JdbcTemplate(ds), embeddingModel)
                .dimensions(dim)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
        vectorStore.afterPropertiesSet();
        return new PgVectorChunkStore(vectorStore, new TransactionTemplate(new DataSourceTransactionManager(ds)));
    }
}
