package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.pipeline.ingest.IngestService;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;
import com.lia.core.testsupport.FakeEmbeddingModel;
import com.lia.core.testsupport.FakeLawFeed;
import com.lia.core.testsupport.LawFixtures;
import com.lia.core.testsupport.TestDatabases;
import com.lia.core.testsupport.TestLaws;

/** 작업 핸들러 4종 — 실 Postgres·pgvector(가짜 임베딩) + 주택법 fixture. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class TaskHandlersIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final String LAW = LawFixtures.HOUSING_LAW_ID;
    static final LocalDate EF = LawFixtures.HOUSING_EFFECTIVE;

    static JdbcClient jdbc;
    static LawStore lawStore;
    static TaskQueue queue;
    static ChunkStore chunkStore;
    static IngestService ingest;
    static TransactionTemplate tx;
    FakeLawFeed feed;
    SyncHandler sync;
    IndexHandler index;
    TransitionHandler transition;
    PurgeHandler purge;

    @BeforeAll
    static void setup() {
        DataSource ds = TestDatabases.migrate(PG);
        jdbc = JdbcClient.create(ds);
        lawStore = new LawStore(jdbc, new ObjectMapper());
        queue = new TaskQueue(jdbc);
        chunkStore = TestDatabases.chunkStore(ds, new FakeEmbeddingModel(8), 8);
        ingest = new IngestService(new Normalizer(), new DiffBuilder(), lawStore, null);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));   // jdbc 와 같은 DataSource
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM ingest_tasks").update();
        jdbc.sql("DELETE FROM law_versions").update();
        jdbc.sql("TRUNCATE TABLE vector_store").update();
        feed = new FakeLawFeed().withHousingAct();
        sync = new SyncHandler(feed, ingest, lawStore, queue, tx);
        index = new IndexHandler(lawStore, new RAGIndexer(chunkStore), new MinIntervalLimiter(Duration.ZERO));
        transition = new TransitionHandler(lawStore, chunkStore, queue, tx);
        purge = new PurgeHandler(lawStore, chunkStore, tx);
    }

    private static Task task(TaskKind kind, String lawId, LocalDate effectiveDate, String mst) {
        return new Task(0, 0, kind, lawId, effectiveDate, mst, 1);
    }

    private List<String> readyTasks() {
        return jdbc.sql("SELECT kind || ':' || law_id || '@' || effective_date FROM ingest_tasks WHERE status = 'READY' ORDER BY task_id")
                .query(String.class).list();
    }

    private int chunks(String lawId, LocalDate effectiveDate) {
        return jdbc.sql("SELECT count(*) FROM vector_store WHERE metadata->>'lawId' = :id AND metadata->>'efYd' = :ef")
                .param("id", lawId).param("ef", effectiveDate.toString())
                .query(Integer.class).single();
    }

    /** 이번 테스트에서 생긴 READY 작업을 완료 처리(다음 단계 검증 전 정리). */
    private void completeAll() {
        jdbc.sql("UPDATE ingest_tasks SET status = 'DONE' WHERE status = 'READY'").update();
    }

    @Test
    @DisplayName("SYNC 신규 — 시행예정·기준선 저장, INDEX 등록")
    void sync_신규() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));

        assertEquals(Law.Status.시행예정, lawStore.find(LAW, EF).orElseThrow().status());
        assertEquals(LawFixtures.HOUSING_BASELINE_EFFECTIVE, lawStore.findBaseline(LAW).orElseThrow().effectiveDate());
        assertEquals(List.of("INDEX:001809@2026-08-04"), readyTasks());
    }

    @Test
    @DisplayName("SYNC 재처리(같은 내용) — INDEX 를 다시 만들지 않는다")
    void sync_변경없음() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        completeAll();

        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));

        assertTrue(readyTasks().isEmpty());
    }

    @Test
    @DisplayName("SYNC 원자성 — INDEX 등록이 실패하면 정본 저장도 롤백(저장만 되고 색인 등록이 빠지지 않게)")
    void sync_원자성() {
        TaskQueue failingQueue = new TaskQueue(jdbc) {
            @Override
            public boolean enqueue(TaskKind kind, String lawId, LocalDate effectiveDate, String mst) {
                throw new IllegalStateException("등록 실패(테스트)");
            }
        };
        SyncHandler failing = new SyncHandler(feed, ingest, lawStore, failingQueue, tx);

        assertThrows(IllegalStateException.class,
                () -> failing.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST)));

        assertTrue(lawStore.find(LAW, EF).isEmpty(), "저장이 함께 롤백돼야 재시도 때 다시 저장·등록된다");
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        assertEquals(List.of("INDEX:001809@2026-08-04"), readyTasks());
    }

    @Test
    @DisplayName("SYNC — 철회된 버전이 같은 내용으로 다시 오면 시행예정 복원 + INDEX")
    void sync_철회본복원() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        purge.handle(task(TaskKind.PURGE, LAW, EF, null));
        completeAll();

        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));

        assertEquals(Law.Status.시행예정, lawStore.find(LAW, EF).orElseThrow().status());
        assertEquals(List.of("INDEX:001809@2026-08-04"), readyTasks());
    }

    @Test
    @DisplayName("SYNC — 기준선보다 오래된 시행중본은 과거로")
    void sync_과거전환() {
        lawStore.upsert(TestLaws.law(LAW, "OLD", Law.Status.시행중, LocalDate.of(2015, 1, 1)));

        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));

        assertEquals(Law.Status.과거, lawStore.find(LAW, LocalDate.of(2015, 1, 1)).orElseThrow().status());
    }

    @Test
    @DisplayName("INDEX — 정본에서 색인. 시행예정이 아니면 아무것도 안 한다")
    void index() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));

        index.handle(task(TaskKind.INDEX, LAW, EF, null));
        assertTrue(chunks(LAW, EF) > 0, "색인됨");

        jdbc.sql("TRUNCATE TABLE vector_store").update();
        lawStore.updateStatus(LAW, EF, Law.Status.시행중);
        index.handle(task(TaskKind.INDEX, LAW, EF, null));
        assertEquals(0, chunks(LAW, EF), "전환된 버전은 색인하지 않는다");
    }

    @Test
    @DisplayName("TRANSITION — 시행중 전환·옛 시행중 과거·색인 제거·남은 시행예정본 SYNC")
    void transition() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        index.handle(task(TaskKind.INDEX, LAW, EF, null));
        LocalDate later = LocalDate.of(2027, 2, 1);
        lawStore.upsert(TestLaws.law(LAW, "SIBLING", Law.Status.시행예정, later));
        completeAll();

        transition.handle(task(TaskKind.TRANSITION, LAW, EF, null));

        assertEquals(Law.Status.시행중, lawStore.find(LAW, EF).orElseThrow().status());
        assertEquals(Law.Status.과거,
                lawStore.find(LAW, LawFixtures.HOUSING_BASELINE_EFFECTIVE).orElseThrow().status());
        assertEquals(0, chunks(LAW, EF), "pending 색인에서 제거");
        assertEquals(List.of("SYNC:001809@2027-02-01"), readyTasks(), "형제 재수집");
    }

    @Test
    @DisplayName("PURGE — 철회로 바꾸고 색인 제거, 행은 남긴다")
    void purge() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        index.handle(task(TaskKind.INDEX, LAW, EF, null));

        purge.handle(task(TaskKind.PURGE, LAW, EF, null));

        assertEquals(Law.Status.철회, lawStore.find(LAW, EF).orElseThrow().status(), "행은 보존");
        assertEquals(0, chunks(LAW, EF));
    }

    /** 색인 제거가 항상 실패하는 ChunkStore — 상태 변경과 색인 제거가 한 트랜잭션인지 확인용. */
    private static ChunkStore failingChunkStore() {
        return new ChunkStore() {
            @Override
            public void replaceVersion(String lawId, String efYd, List<Chunk> chunks) {
                throw new IllegalStateException("색인 제거 실패(테스트)");
            }

            @Override
            public List<Chunk> search(String query, int topK) {
                return List.of();
            }
        };
    }

    @Test
    @DisplayName("TRANSITION 원자성 — 색인 제거가 실패하면 상태·기준선·SYNC 등록 모두 롤백")
    void transition_원자성() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        lawStore.upsert(TestLaws.law(LAW, "SIBLING", Law.Status.시행예정, LocalDate.of(2027, 2, 1)));
        completeAll();
        TransitionHandler failing = new TransitionHandler(lawStore, failingChunkStore(), queue, tx);

        assertThrows(IllegalStateException.class,
                () -> failing.handle(task(TaskKind.TRANSITION, LAW, EF, null)));

        assertEquals(Law.Status.시행예정, lawStore.find(LAW, EF).orElseThrow().status());
        assertEquals(Law.Status.시행중,
                lawStore.find(LAW, LawFixtures.HOUSING_BASELINE_EFFECTIVE).orElseThrow().status(),
                "옛 시행중본이 과거로 바뀌면 안 된다");
        assertTrue(readyTasks().isEmpty(), "형제 SYNC 가 등록되면 안 된다");
    }

    @Test
    @DisplayName("PURGE 원자성 — 색인 제거가 실패하면 철회 처리도 롤백")
    void purge_원자성() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        PurgeHandler failing = new PurgeHandler(lawStore, failingChunkStore(), tx);

        assertThrows(IllegalStateException.class,
                () -> failing.handle(task(TaskKind.PURGE, LAW, EF, null)));

        assertEquals(Law.Status.시행예정, lawStore.find(LAW, EF).orElseThrow().status());
    }

    @Test
    @DisplayName("오래된 작업 무시 — 시행예정이 아닌 버전에는 TRANSITION·PURGE 가 아무것도 안 한다")
    void 오래된작업_무시() {
        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));
        purge.handle(task(TaskKind.PURGE, LAW, EF, null));

        transition.handle(task(TaskKind.TRANSITION, LAW, EF, null));

        assertEquals(Law.Status.철회, lawStore.find(LAW, EF).orElseThrow().status(), "철회 행이 되살아나면 안 된다");

        sync.handle(task(TaskKind.SYNC, LAW, EF, LawFixtures.HOUSING_MST));   // 철회본 복원 → 시행예정
        transition.handle(task(TaskKind.TRANSITION, LAW, EF, null));
        purge.handle(task(TaskKind.PURGE, LAW, EF, null));

        assertEquals(Law.Status.시행중, lawStore.find(LAW, EF).orElseThrow().status(), "시행중본이 철회되면 안 된다");
    }

    @Test
    @DisplayName("핸들러마다 종류가 하나씩")
    void 종류() {
        assertEquals(Set.of(TaskKind.values()),
                Set.of(sync.kind(), index.kind(), transition.kind(), purge.kind()));
    }
}
