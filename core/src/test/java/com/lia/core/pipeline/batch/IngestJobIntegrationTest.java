package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.junit.jupiter.api.Timeout;
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

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisContext;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.analyze.GroundedSummaries;
import com.lia.core.pipeline.connector.LawApiException;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.index.RAGIndexer;
import com.lia.core.pipeline.ingest.IngestService;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawStore;
import com.lia.core.store.LawSummaries;
import com.lia.core.store.LawSummaryStore;
import com.lia.core.store.NotificationStore;
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
    static LawSummaryStore summaries;
    /** 지금 근거 집합 안 인용만 인정하는 읽기 — 운영 배선과 같은 구성. */
    static LawSummaries groundedSummaries;
    /** groundedEngine 의 추론기 호출 수 — 유료 호출이 없었는지 확인용. */
    static final AtomicInteger reasonerCalls = new AtomicInteger();
    static IngestService ingest;
    static NotificationStore notifications;
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
        summaries = new LawSummaryStore(jdbc, new ObjectMapper(), "p-test");
        groundedSummaries = new GroundedSummaries(summaries, new ContextBuilder());
        notifications = new NotificationStore(jdbc);
        chunkStore = TestDatabases.chunkStore(ds, new FakeEmbeddingModel(8), 8);
        ingest = new IngestService(new Normalizer(), new DiffBuilder(), lawStore, null);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM ingest_tasks").update();
        jdbc.sql("DELETE FROM ingest_runs").update();
        jdbc.sql("DELETE FROM law_summaries").update();
        jdbc.sql("DELETE FROM notifications").update();
        jdbc.sql("DELETE FROM law_subscriptions").update();
        reasonerCalls.set(0);
        jdbc.sql("DELETE FROM law_versions").update();
        jdbc.sql("TRUNCATE TABLE vector_store").update();
        feed = new FakeLawFeed().withHousingAct();
    }

    /** 오늘을 정한 잡 — 워커 2, 대기 간격 짧게, 색인 켜짐·요약 꺼짐(유료 기본). */
    private IngestJob job(LocalDate today) {
        return job(today, true, false);
    }

    private IngestJob job(LocalDate today, boolean indexEnabled) {
        return job(today, indexEnabled, false);
    }

    private IngestJob job(LocalDate today, boolean indexEnabled, boolean summaryEnabled) {
        return job(today, indexEnabled, summaryEnabled, 2, null);
    }

    /** 주입된 첫 source_id 를 인용해 늘 검증을 통과하는 추론기. */
    private static AnalysisEngine groundedEngine() {
        return new AnalysisEngine(new ContextBuilder(), ctx -> countedSummary(ctx), 2);
    }

    private static ImpactResult countedSummary(AnalysisContext ctx) {
        reasonerCalls.incrementAndGet();
        return new ImpactResult(ctx.lawRef(), "SUMMARY", "쉬운 요약",
                List.of(new ImpactResult.Claim("바뀐다", List.of(ctx.injectedSourceIds().iterator().next()), 0.9)),
                List.of(), List.of(), null, List.of(), "참고용", null);
    }

    /** @param sync null 이면 실제 SyncHandler — 워커 동작을 조종하는 테스트가 바꿔 끼운다 */
    private IngestJob job(LocalDate today, boolean indexEnabled, boolean summaryEnabled, int workers,
                          TaskHandler sync) {
        return job(today, indexEnabled, summaryEnabled, workers, sync, groundedEngine());
    }

    /** @param summaryEngine 요약 추론에 쓸 엔진 — 추론 실패 처리를 확인하는 테스트가 바꿔 끼운다 */
    private IngestJob job(LocalDate today, boolean indexEnabled, boolean summaryEnabled, int workers,
                          TaskHandler sync, AnalysisEngine summaryEngine) {
        return job(today, indexEnabled, summaryEnabled, workers, sync, summaryEngine, notifications);
    }

    /** @param dueNotifications 구독 알림 생성 — 끝 단계 실패를 확인하는 테스트가 바꿔 끼운다 */
    private IngestJob job(LocalDate today, boolean indexEnabled, boolean summaryEnabled, int workers,
                          TaskHandler sync, AnalysisEngine summaryEngine, DueNotifications dueNotifications) {
        Clock clock = Clock.fixed(today.atStartOfDay(SEOUL).toInstant(), SEOUL);
        Discoverer discoverer = new Discoverer(feed, lawStore, queue, clock, 10);
        List<TaskHandler> handlers = List.of(
                sync != null ? sync : new SyncHandler(feed, ingest, lawStore, queue, tx, clock),
                new IndexHandler(lawStore, new RAGIndexer(chunkStore), new MinIntervalLimiter(Duration.ZERO)),
                new SummarizeHandler(lawStore, summaryEngine, summaries, groundedSummaries, new MinIntervalLimiter(Duration.ZERO), "test-model"),
                new TransitionHandler(lawStore, chunkStore, queue, tx),
                new PurgeHandler(lawStore, chunkStore, tx));
        return new IngestJob(queue, runs, discoverer, lawStore, groundedSummaries, dueNotifications, handlers, workers, indexEnabled,
                summaryEnabled, Duration.ofMillis(20), Duration.ofDays(30), clock, null);
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
    @DisplayName("요약 꺼짐(기본) — run 은 SUMMARIZE 를 미루고(SUCCESS·deferred), sync 명령도 처리하지 않으며, summarize 명령만 처리")
    void 요약_유료보호() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        RunReport collected = job(today).run(run());
        assertEquals(RunOutcome.SUCCESS, collected.outcome());
        assertEquals(1, collected.counts().get("deferred.SUMMARIZE"));

        RunReport synced = job(today)
                .run(new JobCommand(JobCommand.Type.SYNC, false, false, LAW, EF, LawFixtures.HOUSING_MST));
        assertEquals(1, synced.counts().get("deferred.SUMMARIZE"), "수동 sync 도 유료 요약은 건드리지 않는다");

        RunReport summarized = job(today)
                .run(new JobCommand(JobCommand.Type.SUMMARIZE, false, false, null, null, null));
        assertEquals(0, summarized.counts().get("deferred.SUMMARIZE"));
        assertTrue(groundedSummaries.findCurrent(lawStore.find(LAW, EF).orElseThrow()).isPresent());
    }

    @Test
    @DisplayName("summarize 명령 — 등록 작업이 없는 기존 시행예정본도 등록해 채우고, 다시 돌리면 추론기를 부르지 않는다")
    void 요약_기존본_채우기() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        job(today).run(run());   // 요약 꺼짐 — 정본만 저장, SUMMARIZE 는 READY 로 남음
        jdbc.sql("DELETE FROM ingest_tasks WHERE kind = 'SUMMARIZE'").update();   // 기능 도입 전에 저장된 법령 흉내
        JobCommand summarize = new JobCommand(JobCommand.Type.SUMMARIZE, false, false, null, null, null);

        RunReport filled = job(today).run(summarize);
        assertEquals(1, filled.counts().get("enqueued.SUMMARIZE"));
        assertEquals(1, filled.counts().get("done"));
        assertTrue(groundedSummaries.findCurrent(lawStore.find(LAW, EF).orElseThrow()).isPresent());
        assertEquals(1, reasonerCalls.get());

        RunReport again = job(today).run(summarize);
        assertEquals(1, again.counts().get("done"), "등록·처리는 되지만");
        assertEquals(1, reasonerCalls.get(), "유효한 요약이 있어 추론기는 부르지 않는다");
    }

    @Test
    @DisplayName("summarize --dry-run — 요약 대상 수만 기록, 등록·추론기 호출 없음")
    void 요약_시험실행() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        job(today).run(run());   // 요약 꺼짐 — 정본 저장, SUMMARIZE 는 READY 로 남음
        int readyBefore = readyCount(TaskKind.SUMMARIZE);
        JobCommand dryRun = new JobCommand(JobCommand.Type.SUMMARIZE, false, true, null, null, null);

        RunReport planned = job(today).run(dryRun);

        assertEquals(RunOutcome.SUCCESS, planned.outcome());
        assertEquals(1, planned.counts().get("planned.SUMMARIZE"));
        assertFalse(planned.counts().containsKey("enqueued.SUMMARIZE"));
        assertEquals(readyBefore, readyCount(TaskKind.SUMMARIZE), "큐에 아무것도 더하지 않는다");
        assertEquals(0, reasonerCalls.get(), "유료 호출 없음");
        assertTrue(groundedSummaries.findCurrent(lawStore.find(LAW, EF).orElseThrow()).isEmpty());

        job(today).run(new JobCommand(JobCommand.Type.SUMMARIZE, false, false, null, null, null));
        RunReport afterFill = job(today).run(dryRun);
        assertEquals(0, afterFill.counts().get("planned.SUMMARIZE"), "유효한 요약이 있으면 대상이 아니다");
    }

    private int readyCount(TaskKind kind) {
        return queue.countReady(kind);
    }

    @Test
    @DisplayName("reindex-all·requeue-dead — SUMMARIZE 는 READY 로 남고 추론기를 부르지 않는다(유료 보호)")
    void 요약_다른명령_보호() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        feed.head("008888", "WRONG", LocalDate.of(2027, 1, 1)).failFetch("WRONG", new LawApiException("잘못된 MST"));
        job(today).run(run());   // 요약 꺼짐 — 주택법 SUMMARIZE 는 READY, WRONG 의 SYNC 는 DEAD
        assertEquals(1, readyCount(TaskKind.SUMMARIZE));

        RunReport reindexed = job(today)
                .run(new JobCommand(JobCommand.Type.REINDEX_ALL, false, false, null, null, null));
        assertEquals(1, reindexed.counts().get("deferred.SUMMARIZE"));
        assertEquals(1, readyCount(TaskKind.SUMMARIZE));

        feed.failFetch("WRONG", null).pendingBody("WRONG", LawFixtures.raw(LawFixtures.HOUSING_PENDING, "시행예정"));
        RunReport requeued = job(today)
                .run(new JobCommand(JobCommand.Type.REQUEUE_DEAD, false, false, null, null, null));
        assertEquals(1, requeued.counts().get("requeued"));
        assertEquals(1, requeued.counts().get("deferred.SUMMARIZE"), "WRONG 의 요약도 새로 READY 로 쌓일 뿐 처리하지 않는다");
        assertEquals(0, reasonerCalls.get(), "run(요약 켜짐)·summarize 밖에서는 유료 추론 0");
    }

    @Test
    @DisplayName("요약 추론이 일시적으로 실패하면(RuntimeException) READY 로 남아 다음 실행에서 다시 시도한다")
    void 요약_일시오류_재시도() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        job(today).run(run());   // SUMMARIZE READY
        AnalysisEngine flaky = new AnalysisEngine(new ContextBuilder(), ctx -> {
            throw new ResourceAccessException("Opus 응답 시간 초과");
        }, 2);

        RunReport failed = job(today, true, false, 2, null, flaky)
                .run(new JobCommand(JobCommand.Type.SUMMARIZE, false, false, null, null, null));

        assertEquals(RunOutcome.PARTIAL, failed.outcome());
        assertEquals(1, failed.counts().get("failed"));
        assertEquals(0, failed.counts().get("dead"));
        assertEquals(1, readyCount(TaskKind.SUMMARIZE), "다음 실행이 다시 시도한다");

        RunReport retried = job(today)
                .run(new JobCommand(JobCommand.Type.SUMMARIZE, false, false, null, null, null));
        assertEquals(1, retried.counts().get("done"));
        assertTrue(groundedSummaries.findCurrent(lawStore.find(LAW, EF).orElseThrow()).isPresent());
    }

    @Test
    @DisplayName("요약 추론 오류의 원인이 파싱 실패(JacksonException)면 ErrorClassifier 정책대로 바로 격리(DEAD)")
    void 요약_파싱오류_격리() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        job(today).run(run());   // SUMMARIZE READY
        AnalysisEngine unparsable = new AnalysisEngine(new ContextBuilder(), ctx -> {
            throw new IllegalStateException("추론 응답 해석 실패", parseFailure());
        }, 2);

        RunReport report = job(today, true, false, 2, null, unparsable)
                .run(new JobCommand(JobCommand.Type.SUMMARIZE, false, false, null, null, null));

        assertEquals(1, report.counts().get("dead"));
        assertEquals(0, readyCount(TaskKind.SUMMARIZE));
        assertEquals(1, queue.countByStatus().get(TaskStatus.DEAD));
    }

    /** 깨진 JSON 을 읽으려다 나는 JacksonException. */
    private static RuntimeException parseFailure() {
        try {
            new ObjectMapper().readValue("{", ImpactResult.class);
        } catch (tools.jackson.core.JacksonException e) {
            return e;
        }
        throw new AssertionError("파싱 실패가 나야 한다");
    }

    @Test
    @DisplayName("요약 켜짐 — run 이 SUMMARIZE 까지 처리")
    void 요약_켜짐() {
        RunReport report = job(LocalDate.of(2026, 7, 1), true, true, 2, null).run(run());
        assertEquals(0, report.counts().get("deferred.SUMMARIZE"));
        assertTrue(groundedSummaries.findCurrent(lawStore.find(LAW, EF).orElseThrow()).isPresent());
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
        IngestJob job = job(LocalDate.of(2026, 7, 1), true, false, 1, null);
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
        IngestJob job = job(LocalDate.of(2026, 7, 1), false, false, 1, null);
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

    /**
     * 남는 워커 = 먼저 제출된 워커(풀의 첫 스레드 {@code -thread-1}), 죽는 워커 = 나중에 제출된 워커 —
     * 제출 순서대로 future 를 기다리면 죽은 워커를 늦게 알아채는 경우를 결정적으로 만든다.
     * 어느 워커가 어느 작업을 가져가든 동작은 스레드로 정한다.
     */
    @Test
    @Timeout(30)
    @DisplayName("워커 하나가 죽으면 즉시 나머지 워커를 중단시키고, 끝날 때까지 기다린 뒤 실행을 FAILED 로 기록")
    void 워커실패_나머지대기() {
        feed.clearHeads().head("A", "MA", LocalDate.of(2027, 1, 1)).head("B", "MB", LocalDate.of(2027, 1, 1));
        CountDownLatch survivorEntered = new CountDownLatch(1);
        AtomicBoolean survivorInterrupted = new AtomicBoolean();
        AtomicBoolean survivorFinished = new AtomicBoolean();
        TaskHandler sync = new TaskHandler() {
            @Override
            public TaskKind kind() {
                return TaskKind.SYNC;
            }

            @Override
            public void handle(Task task) {
                if (Thread.currentThread().getName().endsWith("-thread-1")) {   // 먼저 제출된 워커
                    survivorEntered.countDown();
                    try {
                        new CountDownLatch(1).await(5, TimeUnit.SECONDS);       // 중단(interrupt)될 때까지 — 회귀 시 멈추지 않게 상한
                    } catch (InterruptedException e) {
                        survivorInterrupted.set(true);
                        pause(Duration.ofMillis(300));   // 중단 후 정리 중 — 기다리지 않으면 이 사이에 실행이 끝난다
                    }
                    survivorFinished.set(true);
                } else {
                    await(survivorEntered);
                    throw new AssertionError("워커 사망(테스트)");
                }
            }
        };

        assertThrows(IllegalStateException.class,
                () -> job(LocalDate.of(2026, 7, 1), true, false, 2, sync).run(run()));

        assertTrue(survivorInterrupted.get(), "죽은 워커를 알아채는 즉시 남은 워커를 중단시킨다(제출 순서와 무관)");
        assertTrue(survivorFinished.get(), "실행 기록·잠금 해제 전에 남은 워커가 끝나야 한다");
        assertEquals("FAILED", jdbc.sql("SELECT outcome FROM ingest_runs WHERE finished_at IS NOT NULL")
                .query(String.class).single());
    }

    @Test
    @DisplayName("러너 종료 훅 — 종료 신호 후 실행이 끝나 기록될 때까지 기다린다")
    void 러너_종료대기() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        feed = blockingFeed(entered, release);
        IngestJobRunner runner = new IngestJobRunner(job(LocalDate.of(2026, 7, 1), true, false, 1, null), dataSource);
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

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (user_id, provider, provider_id, created_at) VALUES (:id, 'kakao', :sub, now())")
                .param("id", id).param("sub", id.toString()).update();
        return id;
    }

    private void version(String lawId, LocalDate effectiveDate, String status, String title) {
        jdbc.sql("""
                INSERT INTO law_versions (law_id, effective_date, revision, status, title, payload, last_seen)
                VALUES (:lawId, :efYd, 'rev', :status, :title, '{}'::jsonb, now())
                """)
                .param("lawId", lawId).param("efYd", effectiveDate).param("status", status).param("title", title).update();
    }

    private void subscribe(UUID userId, String lawId, int notifyBefore) {
        jdbc.sql("INSERT INTO law_subscriptions (user_id, law_id, notify_before, created_at) VALUES (:u, :l, :n, now())")
                .param("u", userId).param("l", lawId).param("n", notifyBefore).update();
    }

    @Test
    @DisplayName("notify 명령 — 시점이 된 구독 알림을 만들고 다시 돌리면 0, --dry-run 은 세기만(D74)")
    void notify_명령() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        UUID userId = account();
        version("001809", today.plusDays(5), "시행예정", "주택법");
        subscribe(userId, "001809", 7);

        RunReport dry = job(today).run(new JobCommand(JobCommand.Type.NOTIFY, false, true, null, null, null));
        assertEquals(1, dry.counts().get("planned.notified"));
        assertEquals(0, jdbc.sql("SELECT count(*) FROM notifications").query(Integer.class).single());

        RunReport first = job(today).run(new JobCommand(JobCommand.Type.NOTIFY, false, false, null, null, null));
        assertEquals(RunOutcome.SUCCESS, first.outcome());
        assertEquals(1, first.counts().get("notified"));
        RunReport again = job(today).run(new JobCommand(JobCommand.Type.NOTIFY, false, false, null, null, null));
        assertEquals(0, again.counts().get("notified"));
    }

    @Test
    @DisplayName("run 은 끝 단계로 notify 를 부르고 notified 를 기록한다 — --dry-run 은 부르지 않는다")
    void run_끝에_notify() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        UUID userId = account();
        version("009999", today.plusDays(1), "시행예정", "가상법");   // 원천에 없는 법령 — 탐색과 섞이지 않게
        subscribe(userId, "009999", 1);

        RunReport dry = job(today).run(new JobCommand(JobCommand.Type.RUN, false, true, null, null, null));
        assertNull(dry.counts().get("notified"));

        RunReport run = job(today).run(new JobCommand(JobCommand.Type.RUN, false, false, null, null, null));
        assertEquals(1, run.counts().get("notified"));
    }

    @Test
    @DisplayName("시행일 당일 run — 소진 중 시행중으로 전환된 그날 버전에도 당일 알림이 간다(D74)")
    void run_시행일_당일_전환된_버전에도_알림() {
        UUID userId = account();
        job(LocalDate.of(2026, 7, 1)).run(run());   // 주택법 시행예정본 수집(시행일 EF)
        subscribe(userId, LAW, 0);

        RunReport onEffectiveDay = job(EF).run(run());   // 시행일 당일 — 소진 중 TRANSITION 이 시행중으로 바꾼다

        assertEquals("시행중", jdbc.sql("SELECT status FROM law_versions WHERE law_id = :id AND effective_date = :ef")
                .param("id", LAW).param("ef", EF).query(String.class).single(), "run 이 TRANSITION 으로 시행중으로 바꿨다");
        assertEquals(1, onEffectiveDay.counts().get("notified"), "전환된 뒤에도 당일 알림을 만든다");
        assertEquals(1, jdbc.sql("SELECT count(*) FROM notifications WHERE law_id = :id AND effective_date = :ef")
                .param("id", LAW).param("ef", EF).query(Integer.class).single());
    }

    @Test
    @DisplayName("run 끝 단계의 알림 생성이 실패해도 수집 결과는 그대로 — notify.failed 만 남긴다(D74)")
    void run_끝_알림_실패는_수집_결과를_바꾸지_않는다() {
        LocalDate today = LocalDate.of(2026, 7, 1);
        DueNotifications failing = new DueNotifications() {
            @Override
            public int createDue(LocalDate day) {
                throw new IllegalStateException("알림 생성 실패(테스트)");
            }

            @Override
            public int countDue(LocalDate day) {
                throw new IllegalStateException("알림 집계 실패(테스트)");
            }
        };

        RunReport run = job(today, true, false, 2, null, groundedEngine(), failing)
                .run(new JobCommand(JobCommand.Type.RUN, false, false, null, null, null));

        assertEquals(RunOutcome.SUCCESS, run.outcome(), "수집이 성공했으면 알림 실패로 FAILED 가 되지 않는다");
        assertEquals(1, run.counts().get("notify.failed"));
        assertNull(run.counts().get("notified"));
        assertTrue(chunks() > 0, "수집·색인은 그대로 끝났다");
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
