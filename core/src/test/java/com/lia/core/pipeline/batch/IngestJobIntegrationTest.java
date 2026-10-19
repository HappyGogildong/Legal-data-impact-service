package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.LawApiException;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.pipeline.ingest.IngestService;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;
import com.lia.core.testsupport.FakeEmbeddingModel;
import com.lia.core.testsupport.FakeLawFeed;
import com.lia.core.testsupport.LawFixtures;
import com.lia.core.testsupport.TestDatabases;

/** 잡 전체 — 탐색 → 큐 소진 → 기록. 실 Postgres·pgvector(가짜 임베딩) + 가짜 원천. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class IngestJobIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final String LAW = LawFixtures.HOUSING_LAW_ID;
    static final LocalDate EF = LawFixtures.HOUSING_EFFECTIVE;

    static DataSource dataSource;
    static JdbcClient jdbc;
    static LawStore lawStore;
    static TaskQueue queue;
    static IngestRunStore runs;
    static ChunkStore chunkStore;
    static IngestService ingest;
    static TransactionTemplate tx;
    FakeLawFeed feed;

    @BeforeAll
    static void setup() {
        DataSource ds = TestDatabases.migrate(PG);
        dataSource = ds;
        jdbc = JdbcClient.create(ds);
        lawStore = new LawStore(jdbc, new ObjectMapper());
        queue = new TaskQueue(jdbc);
        runs = new IngestRunStore(jdbc, new ObjectMapper());
        chunkStore = TestDatabases.chunkStore(ds, new FakeEmbeddingModel(8), 8);
        ingest = new IngestService(new Normalizer(), new DiffBuilder(), lawStore, null);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM ingest_tasks").update();
        jdbc.sql("DELETE FROM ingest_runs").update();
        jdbc.sql("DELETE FROM law_versions").update();
        jdbc.sql("TRUNCATE TABLE vector_store").update();
        feed = new FakeLawFeed().withHousingAct();
    }

    /** 오늘을 정한 잡 — 워커 2, 대기 간격 짧게, 색인 켜짐. */
    private IngestJob job(LocalDate today) {
        return job(today, true);
    }

    private IngestJob job(LocalDate today, boolean indexEnabled) {
        return job(today, indexEnabled, 2, null);
    }

    /** @param sync null 이면 실제 SyncHandler — 워커 동작을 조종하는 테스트가 바꿔 끼운다 */
    private IngestJob job(LocalDate today, boolean indexEnabled, int workers, TaskHandler sync) {
        Clock clock = Clock.fixed(today.atStartOfDay(SEOUL).toInstant(), SEOUL);
        Discoverer discoverer = new Discoverer(feed, lawStore, queue, clock, 10);
        List<TaskHandler> handlers = List.of(
                sync != null ? sync : new SyncHandler(feed, ingest, lawStore, queue, tx, clock),
                new IndexHandler(lawStore, new RAGIndexer(chunkStore), new MinIntervalLimiter(Duration.ZERO)),
                new TransitionHandler(lawStore, chunkStore, queue, tx),
                new PurgeHandler(lawStore, chunkStore, tx));
        return new IngestJob(queue, runs, discoverer, lawStore, handlers, workers, indexEnabled, Duration.ofMillis(20),
                Duration.ofDays(30), clock, null);
    }

    /** 본문 fetch 에서 멈추는 주택법 원천 — 진입하면 {@code entered}, {@code release} 가 열릴 때까지 대기. */
    private static FakeLawFeed blockingFeed(CountDownLatch entered, CountDownLatch release) {
        return new FakeLawFeed() {
            @Override
            public RawLaw fetchPending(String mst, LocalDate effectiveDate) {
                entered.countDown();
                await(release);
                return super.fetchPending(mst, effectiveDate);
            }
        }.withHousingAct();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("대기 시간 초과(테스트)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static JobCommand run() {
        return new JobCommand(JobCommand.Type.RUN, false, false, null, null, null);
    }

    private int chunks() {
        return jdbc.sql("SELECT count(*) FROM vector_store WHERE metadata->>'lawId' = :id AND metadata->>'efYd' = :ef")
                .param("id", LAW).param("ef", EF.toString()).query(Integer.class).single();
    }

    @Test
    @DisplayName("첫 실행은 수집·색인, 두 번째 실행은 아무것도 하지 않는다(멱등)")
    void 멱등() {
        RunReport first = job(LocalDate.of(2026, 7, 1)).run(run());

        assertEquals(RunOutcome.SUCCESS, first.outcome());
        assertEquals(2, first.counts().get("done"), "SYNC + INDEX");
        assertTrue(chunks() > 0);
        int fetches = feed.fetchCount();

        RunReport second = job(LocalDate.of(2026, 7, 1)).run(run());

        assertEquals(RunOutcome.SUCCESS, second.outcome());
        assertEquals(0, second.counts().get("done"));
        assertEquals(fetches, feed.fetchCount(), "MST 가 같으면 본문을 다시 받지 않는다");
    }

    @Test
    @DisplayName("일시적 오류는 다른 법령을 막지 않고, 3번 실행 실패하면 격리된 뒤 재등록되지 않는다")
    void 일시오류_격리() {
        feed.head("009999", "BROKEN", LocalDate.of(2027, 1, 1))
                .failFetch("BROKEN", new ResourceAccessException("timeout", new IOException("read timed out")));

        RunReport first = job(LocalDate.of(2026, 7, 1)).run(run());
        assertEquals(RunOutcome.PARTIAL, first.outcome());
        assertEquals(Law.Status.시행예정, lawStore.find(LAW, EF).orElseThrow().status(), "다른 법령은 처리됨");
        assertEquals(1, first.counts().get("failed"));

        job(LocalDate.of(2026, 7, 1)).run(run());
        RunReport third = job(LocalDate.of(2026, 7, 1)).run(run());
        assertEquals(1, third.counts().get("dead"), "세 번째 실패에 격리");

        RunReport fourth = job(LocalDate.of(2026, 7, 1)).run(run());
        assertNull(fourth.counts().get("enqueued.SYNC"), "격리된 작업은 다시 등록되지 않는다");
        assertEquals(1, queue.countByStatus().get(TaskStatus.DEAD));
    }

    @Test
    @DisplayName("영구 오류는 첫 실패에 바로 격리")
    void 영구오류_즉시격리() {
        feed.head("008888", "WRONG", LocalDate.of(2027, 1, 1)).failFetch("WRONG", new LawApiException("잘못된 MST"));

        RunReport report = job(LocalDate.of(2026, 7, 1)).run(run());

        assertEquals(RunOutcome.PARTIAL, report.outcome());
        assertEquals(1, report.counts().get("dead"));
    }

    @Test
    @DisplayName("시행일이 지나면 다음 실행이 시행중으로 전환하고 색인을 제거한다")
    void 시행전환() {
        job(LocalDate.of(2026, 7, 1)).run(run());
        assertTrue(chunks() > 0);

        feed.clearHeads();   // 시행된 법령은 시행예정 목록에서 빠진다
        RunReport report = job(LocalDate.of(2026, 8, 5)).run(run());

        assertEquals(RunOutcome.SUCCESS, report.outcome());
        assertEquals(Law.Status.시행중, lawStore.find(LAW, EF).orElseThrow().status());
        assertEquals(0, chunks());
    }

    @Test
    @DisplayName("--dry-run 은 계획만 기록하고 아무것도 저장하지 않는다")
    void 시험실행() {
        RunReport report = job(LocalDate.of(2026, 7, 1))
                .run(new JobCommand(JobCommand.Type.RUN, false, true, null, null, null));

        assertEquals(RunOutcome.SUCCESS, report.outcome());
        assertEquals(1, report.counts().get("planned.SYNC"));
        assertTrue(lawStore.find(LAW, EF).isEmpty());
        assertTrue(queue.countByStatus().isEmpty());
    }

    @Test
    @DisplayName("색인 꺼짐 — run 은 수집만 하고 INDEX 를 미룸(SUCCESS), index 명령이 밀린 색인을 처리")
    void 색인끄기_수집만() {
        RunReport collected = job(LocalDate.of(2026, 7, 1), false).run(run());

        assertEquals(RunOutcome.SUCCESS, collected.outcome(), "의도된 미룸은 실패가 아니다");
        assertEquals(Law.Status.시행예정, lawStore.find(LAW, EF).orElseThrow().status(), "수집은 됨");
        assertEquals(0, chunks(), "색인은 안 됨");
        assertEquals(1, collected.counts().get("deferred.INDEX"));

        RunReport indexed = job(LocalDate.of(2026, 7, 1), false)
                .run(new JobCommand(JobCommand.Type.INDEX, false, false, null, null, null));

        assertEquals(RunOutcome.SUCCESS, indexed.outcome());
        assertEquals(1, indexed.counts().get("done"), "직접 명령은 설정과 무관하게 색인");
        assertTrue(chunks() > 0);
        assertEquals(0, indexed.counts().get("deferred.INDEX"));
    }

    @Test
    @DisplayName("--dry-run 도 목록을 끝까지 못 받으면 PARTIAL")
    void 시험실행_목록실패() {
        feed.failList(new IllegalStateException("목록 API 장애"));

        RunReport report = job(LocalDate.of(2026, 7, 1))
                .run(new JobCommand(JobCommand.Type.RUN, false, true, null, null, null));

        assertEquals(RunOutcome.PARTIAL, report.outcome());
    }

    @Test
    @DisplayName("강제 종료로 열린 채 남은 실행은 다음 실행이 FAILED 로 닫는다")
    void 열린실행_정리() {
        long orphan = runs.start("run");          // 죽은 실행 흉내 — finish 없이 남김

        job(LocalDate.of(2026, 7, 1)).run(run());

        assertEquals("FAILED", jdbc.sql("SELECT outcome FROM ingest_runs WHERE run_id = :id")
                .param("id", orphan).query(String.class).single());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM ingest_runs WHERE finished_at IS NULL")
                .query(Integer.class).single());
    }

    @Test
    @DisplayName("sync — 저장된 시행예정본의 MST 로 단건 재처리, 저장 안 됐고 MST 도 없으면 거부")
    void 단건재처리() {
        job(LocalDate.of(2026, 7, 1)).run(run());

        RunReport report = job(LocalDate.of(2026, 7, 1))
                .run(new JobCommand(JobCommand.Type.SYNC, false, false, LAW, EF, null));
        assertEquals(RunOutcome.SUCCESS, report.outcome());
        assertEquals(1, report.counts().get("done"), "내용이 같아 INDEX 는 안 생김");

        assertThrows(IllegalArgumentException.class, () -> job(LocalDate.of(2026, 7, 1))
                .run(new JobCommand(JobCommand.Type.SYNC, false, false, "777777", EF, null)));
    }

    @Test
    @DisplayName("requeue-dead — 격리 작업을 다시 큐에 넣고 처리한다")
    void 격리재투입() {
        feed.head("008888", "WRONG", LocalDate.of(2027, 1, 1)).failFetch("WRONG", new LawApiException("잘못된 MST"));
        job(LocalDate.of(2026, 7, 1)).run(run());
        feed.failFetch("WRONG", null).pendingBody("WRONG", LawFixtures.raw(LawFixtures.HOUSING_PENDING, "시행예정"));

        RunReport report = job(LocalDate.of(2026, 7, 1))
                .run(new JobCommand(JobCommand.Type.REQUEUE_DEAD, false, false, null, null, null));

        assertEquals(1, report.counts().get("requeued"));
        assertNull(queue.countByStatus().get(TaskStatus.DEAD));
    }

    @Test
    @DisplayName("종료 신호 — 진행 중 작업은 끝내고 실행을 기록, 남은 READY 가 있으면 PARTIAL")
    void 종료신호_진행중작업_마침() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        feed = blockingFeed(entered, release);
        IngestJob job = job(LocalDate.of(2026, 7, 1), true, 1, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RunReport> running = executor.submit(() -> job.run(run()));
            await(entered);                 // SYNC 가 진행 중

            job.requestStop();
            release.countDown();
            RunReport report = running.get(30, TimeUnit.SECONDS);

            assertEquals(RunOutcome.PARTIAL, report.outcome(), "SYNC 가 만든 INDEX 가 READY 로 남음");
            assertEquals(1, report.counts().get("done"), "진행 중이던 SYNC 는 완료 기록");
            assertEquals(Law.Status.시행예정, lawStore.find(LAW, EF).orElseThrow().status());
            assertEquals(1, queue.countReady(TaskKind.INDEX));
            assertEquals("PARTIAL", jdbc.sql("SELECT outcome FROM ingest_runs WHERE run_id = :id AND finished_at IS NOT NULL")
                    .param("id", report.runId()).query(String.class).single());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("종료 신호 + 색인 꺼짐 — 미룬 INDEX 는 이번 실행이 처리할 종류가 아니라 PARTIAL 이 아니다")
    void 종료신호_색인꺼짐() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        feed = blockingFeed(entered, release);
        IngestJob job = job(LocalDate.of(2026, 7, 1), false, 1, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RunReport> running = executor.submit(() -> job.run(run()));
            await(entered);

            job.requestStop();
            release.countDown();
            RunReport report = running.get(30, TimeUnit.SECONDS);

            assertEquals(RunOutcome.SUCCESS, report.outcome());
            assertEquals(1, report.counts().get("deferred.INDEX"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("워커 하나가 죽으면 나머지 워커가 끝날 때까지 기다린 뒤 실행을 FAILED 로 기록")
    void 워커실패_나머지대기() {
        feed.clearHeads().head("A", "MA", LocalDate.of(2027, 1, 1)).head("B", "MB", LocalDate.of(2027, 1, 1));
        CountDownLatch bEntered = new CountDownLatch(1);
        AtomicBoolean bFinished = new AtomicBoolean();
        TaskHandler sync = new TaskHandler() {
            @Override
            public TaskKind kind() {
                return TaskKind.SYNC;
            }

            @Override
            public void handle(Task task) {
                if (task.lawId().equals("B")) {
                    bEntered.countDown();
                    try {
                        new CountDownLatch(1).await();        // 중단(interrupt)될 때까지
                    } catch (InterruptedException e) {
                        pause(Duration.ofMillis(300));        // 중단 후 정리 중 — 기다리지 않으면 이 사이에 실행이 끝난다
                        bFinished.set(true);
                    }
                } else {
                    await(bEntered);
                    throw new AssertionError("워커 사망(테스트)");
                }
            }
        };

        assertThrows(IllegalStateException.class,
                () -> job(LocalDate.of(2026, 7, 1), true, 2, sync).run(run()));

        assertTrue(bFinished.get(), "실행 기록·잠금 해제 전에 남은 워커가 끝나야 한다");
        assertEquals("FAILED", jdbc.sql("SELECT outcome FROM ingest_runs WHERE finished_at IS NOT NULL")
                .query(String.class).single());
    }

    @Test
    @DisplayName("러너 종료 훅 — 종료 신호 후 실행이 끝나 기록될 때까지 기다린다")
    void 러너_종료대기() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        feed = blockingFeed(entered, release);
        IngestJobRunner runner = new IngestJobRunner(job(LocalDate.of(2026, 7, 1), true, 1, null), dataSource);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> runner.run(new DefaultApplicationArguments("run")));
            await(entered);

            Thread hook = new Thread(runner::destroy);   // SIGTERM 종료 훅 흉내
            hook.start();
            while (hook.getState() != Thread.State.TIMED_WAITING && hook.getState() != Thread.State.WAITING
                    && hook.getState() != Thread.State.TERMINATED) {
                Thread.onSpinWait();                     // 종료 훅이 실행 종료를 기다리기 시작할 때까지
            }
            release.countDown();
            hook.join(30_000);

            assertEquals(2, runner.getExitCode(), "종료 훅이 끝났을 때 실행은 이미 PARTIAL 로 끝나 있다");
            assertEquals(0, jdbc.sql("SELECT count(*) FROM ingest_runs WHERE finished_at IS NULL")
                    .query(Integer.class).single());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
