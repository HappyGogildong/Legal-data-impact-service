package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.pipeline.connector.LawApiException;
import com.lia.core.testsupport.TestDatabases;

/** 작업 큐·시도 이력·실행 기록 — 실 Postgres 의 부분 유니크·CHECK·SKIP LOCKED·CTE 동작을 검증. Docker 없으면 스킵. */
@Testcontainers(disabledWithoutDocker = true)
class TaskQueueIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final LocalDate EF = LocalDate.of(2027, 1, 1);
    static final Set<TaskKind> ALL_KINDS = EnumSet.allOf(TaskKind.class);
    static final RuntimeException TIMEOUT = new RuntimeException("타임아웃", new IOException("read timed out"));
    static JdbcClient jdbc;
    static TaskQueue queue;
    static IngestRunStore runs;

    @BeforeAll
    static void setup() {
        jdbc = JdbcClient.create(TestDatabases.migrate(PG));
        queue = new TaskQueue(jdbc);
        runs = new IngestRunStore(jdbc, new ObjectMapper());
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM ingest_task_attempts").update();
        jdbc.sql("DELETE FROM ingest_tasks").update();
        jdbc.sql("DELETE FROM ingest_runs").update();
    }

    /** 작업의 시도 행 outcome(오래된 순, 미종료는 "OPEN"). */
    private List<String> attempts(long taskId) {
        return jdbc.sql("SELECT coalesce(outcome, 'OPEN') FROM ingest_task_attempts WHERE task_id = :id ORDER BY attempt_id")
                .param("id", taskId).query(String.class).list();
    }

    private String status(long taskId) {
        return jdbc.sql("SELECT status FROM ingest_tasks WHERE task_id = :id").param("id", taskId)
                .query(String.class).single();
    }

    @Test
    @DisplayName("같은 (종류, 법령 버전) 미완료 작업은 하나만 — 두 번째 등록은 무시")
    void 중복등록_무시() {
        assertTrue(queue.enqueue(TaskKind.SYNC, "A", EF, "M1"));
        assertFalse(queue.enqueue(TaskKind.SYNC, "A", EF, "M1"));
        assertTrue(queue.enqueue(TaskKind.INDEX, "A", EF, null), "종류가 다르면 별개");
        assertEquals(2, queue.countByStatus().get(TaskStatus.READY));
    }

    @Test
    @DisplayName("SYNC 만 mst 를 가진다 — CHECK 위반은 등록 실패")
    void mst제약() {
        assertThrows(DataIntegrityViolationException.class, () -> queue.enqueue(TaskKind.SYNC, "A", EF, null));
        assertThrows(DataIntegrityViolationException.class, () -> queue.enqueue(TaskKind.INDEX, "A", EF, "M1"));
    }

    @Test
    @DisplayName("가져가기는 시도 행을 함께 만들고, 완료하면 시도 행이 DONE")
    void 가져가기_시도기록() {
        long run = runs.start("run");
        queue.enqueue(TaskKind.SYNC, "A", EF, "M1");

        Task task = queue.claim(run, Set.of(), ALL_KINDS).orElseThrow();
        assertEquals(1, task.attempts());
        assertEquals(List.of("OPEN"), attempts(task.taskId()));
        assertEquals(run, jdbc.sql("SELECT run_id FROM ingest_task_attempts WHERE attempt_id = :id")
                .param("id", task.attemptId()).query(Long.class).single());

        assertTrue(queue.complete(task, run));
        assertEquals(List.of("DONE"), attempts(task.taskId()));
        assertEquals("DONE", status(task.taskId()));
    }

    @Test
    @DisplayName("DEAD 는 재등록을 막고, requeueDead 는 같은 행을 READY 로 — task_id·시도 이력 유지")
    void 격리_재투입_이력유지() {
        long run1 = runs.start("run");
        queue.enqueue(TaskKind.SYNC, "A", EF, "M1");
        Task task = queue.claim(run1, Set.of(), ALL_KINDS).orElseThrow();
        assertEquals(TaskStatus.DEAD, queue.fail(task, run1, true, new LawApiException("영구 오류")).orElseThrow());
        assertFalse(queue.enqueue(TaskKind.SYNC, "A", EF, "M1"), "격리된 작업은 다시 등록되지 않는다");

        assertEquals(1, queue.requeueDead());
        long run2 = runs.start("requeue-dead");
        Task again = queue.claim(run2, Set.of(), ALL_KINDS).orElseThrow();

        assertEquals(task.taskId(), again.taskId(), "같은 작업 행");
        assertEquals(1, again.attempts(), "시도 횟수는 0 에서 다시");
        assertEquals(List.of("DEAD", "OPEN"), attempts(task.taskId()), "이전 시도 이력 보존");
    }

    @Test
    @DisplayName("DONE 이후에는 같은 작업을 새로 등록할 수 있다")
    void 완료후_재등록() {
        long run = runs.start("run");
        queue.enqueue(TaskKind.INDEX, "A", EF, null);
        queue.complete(queue.claim(run, Set.of(), ALL_KINDS).orElseThrow(), run);

        assertTrue(queue.enqueue(TaskKind.INDEX, "A", EF, null));
    }

    @Test
    @DisplayName("우선순위 — TRANSITION·PURGE → SYNC → INDEX")
    void 우선순위() {
        long run = runs.start("run");
        queue.enqueue(TaskKind.INDEX, "A", EF, null);
        queue.enqueue(TaskKind.SYNC, "B", EF, "M");
        queue.enqueue(TaskKind.TRANSITION, "C", EF, null);

        assertEquals(TaskKind.TRANSITION, queue.claim(run, Set.of(), ALL_KINDS).orElseThrow().kind());
        assertEquals(TaskKind.SYNC, queue.claim(run, Set.of(), ALL_KINDS).orElseThrow().kind());
        assertEquals(TaskKind.INDEX, queue.claim(run, Set.of(), ALL_KINDS).orElseThrow().kind());
    }

    @Test
    @DisplayName("종류 필터 — 이번 실행이 처리하지 않는 종류(색인 꺼짐 → INDEX)는 가져가지 않고 READY 로 남는다")
    void 종류필터() {
        long run = runs.start("run");
        queue.enqueue(TaskKind.INDEX, "A", EF, null);
        queue.enqueue(TaskKind.SYNC, "B", EF, "M");
        Set<TaskKind> withoutIndex = EnumSet.complementOf(EnumSet.of(TaskKind.INDEX));

        assertEquals(TaskKind.SYNC, queue.claim(run, Set.of(), withoutIndex).orElseThrow().kind());
        assertTrue(queue.claim(run, Set.of(), withoutIndex).isEmpty());
        assertEquals(1, queue.countReady(TaskKind.INDEX));
        assertEquals(TaskKind.INDEX, queue.claim(run, Set.of(), EnumSet.of(TaskKind.INDEX)).orElseThrow().kind());
    }

    @Test
    @DisplayName("처리 중인 법령의 작업은 가져가지 않는다")
    void 처리중법령_제외() {
        long run = runs.start("run");
        queue.enqueue(TaskKind.SYNC, "A", EF, "M");

        assertTrue(queue.claim(run, Set.of("A"), ALL_KINDS).isEmpty());
        assertTrue(queue.claim(run, Set.of(), ALL_KINDS).isPresent());
    }

    @Test
    @DisplayName("실행당 1회 — 이번 실행에서 실패한 작업은 다시 안 잡히고 다음 실행에서 잡힌다")
    void 실행당1회() {
        long run1 = runs.start("run");
        queue.enqueue(TaskKind.SYNC, "A", EF, "M");
        Task first = queue.claim(run1, Set.of(), ALL_KINDS).orElseThrow();
        assertEquals(TaskStatus.READY, queue.fail(first, run1, false, TIMEOUT).orElseThrow());
        assertEquals(List.of("RETRY"), attempts(first.taskId()));

        assertTrue(queue.claim(run1, Set.of(), ALL_KINDS).isEmpty());
        long run2 = runs.start("run");
        assertEquals(2, queue.claim(run2, Set.of(), ALL_KINDS).orElseThrow().attempts());
    }

    @Test
    @DisplayName("일시적 오류 3회 → DEAD, 시도 행 RETRY·RETRY·DEAD")
    void 일시오류_3회격리() {
        queue.enqueue(TaskKind.SYNC, "A", EF, "M");
        Task task = null;
        TaskStatus last = null;
        for (int i = 0; i < TaskQueue.MAX_ATTEMPTS; i++) {
            long run = runs.start("run");
            task = queue.claim(run, Set.of(), ALL_KINDS).orElseThrow();
            last = queue.fail(task, run, false, TIMEOUT).orElseThrow();
        }
        assertEquals(TaskStatus.DEAD, last);
        assertEquals(List.of("RETRY", "RETRY", "DEAD"), attempts(task.taskId()));
    }

    @Test
    @DisplayName("소유권 — 복구 후 다른 실행이 가져간 작업은 옛 실행이 완료·실패로 덮지 못한다")
    void 소유권상실_무시() {
        long run1 = runs.start("run");
        queue.enqueue(TaskKind.SYNC, "A", EF, "M");
        Task stale = queue.claim(run1, Set.of(), ALL_KINDS).orElseThrow();
        queue.recoverAbandoned();                       // 새 실행의 정리 단계(옛 작업자는 아직 살아 있다고 가정)
        long run2 = runs.start("run");
        Task current = queue.claim(run2, Set.of(), ALL_KINDS).orElseThrow();

        assertFalse(queue.complete(stale, run1), "늦게 복귀한 옛 작업자");
        assertTrue(queue.fail(stale, run1, true, new LawApiException("늦은 실패")).isEmpty());
        assertEquals("RUNNING", status(current.taskId()), "새 실행 소유 그대로");

        assertTrue(queue.complete(current, run2));
        assertEquals(List.of("ABANDONED", "DONE"), attempts(current.taskId()));
    }

    @Test
    @DisplayName("중단 복구 — 남은 RUNNING 은 READY, 끝나지 못한 시도는 ABANDONED, 시도 횟수 유지")
    void 중단복구() {
        long run1 = runs.start("run");
        queue.enqueue(TaskKind.SYNC, "A", EF, "M");
        Task task = queue.claim(run1, Set.of(), ALL_KINDS).orElseThrow();

        assertEquals(1, queue.recoverAbandoned());
        assertEquals("READY", status(task.taskId()));
        assertEquals(List.of("ABANDONED"), attempts(task.taskId()));

        long run2 = runs.start("run");
        assertEquals(2, queue.claim(run2, Set.of(), ALL_KINDS).orElseThrow().attempts());
    }

    @Test
    @DisplayName("중단 복구 — 시도 한도에 닿은 작업은 DEAD(프로세스를 죽이는 작업의 무한 반복 방지)")
    void 중단복구_한도격리() {
        queue.enqueue(TaskKind.SYNC, "A", EF, "M");
        long taskId = 0;
        for (int i = 0; i < TaskQueue.MAX_ATTEMPTS; i++) {
            taskId = queue.claim(runs.start("run"), Set.of(), ALL_KINDS).orElseThrow().taskId();
            queue.recoverAbandoned();
        }
        assertEquals("DEAD", status(taskId));
    }

    @Test
    @DisplayName("SKIP LOCKED — 동시에 가져가도 같은 작업을 두 번 잡지 않는다")
    void 동시가져가기() throws Exception {
        long run = runs.start("run");
        for (int i = 0; i < 8; i++) {
            queue.enqueue(TaskKind.SYNC, "L" + i, EF, "M");
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Long> claim = () -> {
                start.await();
                return queue.claim(run, Set.of(), ALL_KINDS).orElseThrow().taskId();
            };
            List<Future<Long>> futures = IntStream.range(0, 8).mapToObj(i -> pool.submit(claim)).toList();
            start.countDown();
            Set<Long> ids = new HashSet<>();
            for (Future<Long> f : futures) {
                ids.add(f.get());
            }
            assertEquals(8, ids.size(), "8개 작업이 서로 다른 워커에게");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("보존 기간 지난 DONE 삭제 — 시도 행도 함께")
    void 완료정리() {
        long run = runs.start("run");
        queue.enqueue(TaskKind.INDEX, "A", EF, null);
        Task task = queue.claim(run, Set.of(), ALL_KINDS).orElseThrow();
        queue.complete(task, run);

        assertEquals(1, queue.purgeDoneBefore(OffsetDateTime.now().plusDays(1)));
        assertNull(queue.countByStatus().get(TaskStatus.DONE));
        assertTrue(attempts(task.taskId()).isEmpty());
    }

    @Test
    @DisplayName("실행 기록 — 시작·종료·결과·건수(jsonb)")
    void 실행기록() {
        long run = runs.start("run --full");

        runs.finish(run, RunOutcome.PARTIAL, true, Map.of("done", 3, "dead", 1));

        var row = jdbc.sql("SELECT command, outcome, list_complete, counts->>'done' AS done, finished_at IS NOT NULL AS finished FROM ingest_runs WHERE run_id = :id")
                .param("id", run)
                .query((rs, i) -> List.of(rs.getString("command"), rs.getString("outcome"),
                        String.valueOf(rs.getBoolean("list_complete")), rs.getString("done"),
                        String.valueOf(rs.getBoolean("finished"))))
                .single();
        assertEquals(List.of("run --full", "PARTIAL", "true", "3", "true"), row);
    }

    @Test
    @DisplayName("열린 실행 정리 — 끝나지 못한 실행은 FAILED·abandoned 로 닫는다")
    void 열린실행_정리() {
        long open = runs.start("run");
        long closed = runs.start("run");
        runs.finish(closed, RunOutcome.SUCCESS, true, Map.of());

        assertEquals(1, runs.closeAbandoned());

        assertEquals("FAILED", jdbc.sql("SELECT outcome FROM ingest_runs WHERE run_id = :id").param("id", open)
                .query(String.class).single());
        assertEquals("1", jdbc.sql("SELECT counts->>'abandoned' FROM ingest_runs WHERE run_id = :id").param("id", open)
                .query(String.class).single());
    }

    @Test
    @DisplayName("종료 기록은 열린 실행에만 — 잠금을 잃은 사이 다음 실행이 닫은 실행을 덮지 않는다")
    void 종료기록_닫힌실행_무시() {
        long run = runs.start("run");
        runs.closeAbandoned();                      // 다음 실행이 정리 단계에서 FAILED 로 닫음

        runs.finish(run, RunOutcome.SUCCESS, true, Map.of("done", 1));   // 늦게 복귀한 옛 실행

        assertEquals("FAILED", jdbc.sql("SELECT outcome FROM ingest_runs WHERE run_id = :id").param("id", run)
                .query(String.class).single());
        assertEquals("1", jdbc.sql("SELECT counts->>'abandoned' FROM ingest_runs WHERE run_id = :id").param("id", run)
                .query(String.class).single());
    }

    @Test
    @DisplayName("종료 기록은 finished_at·outcome 이 둘 다 있거나 둘 다 없다(CHECK)")
    void 종료기록_제약() {
        long run = runs.start("run");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("UPDATE ingest_runs SET finished_at = now() WHERE run_id = :id").param("id", run).update());
    }
}
