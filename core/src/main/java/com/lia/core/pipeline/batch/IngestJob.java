package com.lia.core.pipeline.batch;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.lia.core.observability.Obs;
import com.lia.core.store.LawStore;
import com.lia.core.store.PendingVersion;

/**
 * 배치 실행 한 번(D62) — 정리(열린 실행 닫기·중단된 작업 복구·DONE 정리) → 명령별 작업 등록(탐색 등) →
 * 워커 풀로 큐 소진 → 기록. 완료·실패는 이번 실행이 소유한 작업에만 반영된다({@link TaskQueue}).
 *
 * <p>소진: 워커가 {@link TaskQueue#claim}으로 작업을 가져가 종류별 {@link TaskHandler}에 맡긴다. 같은 법령의
 * 작업이 동시에 돌지 않도록 처리 중 법령 집합을 들고 가져가기에서 제외하며, "가져가기 + 집합 등록"은 직렬로 한다.
 * 가져갈 작업이 없어도 진행 중 작업이 있으면 기다렸다 다시 가져가고(새 작업이 생길 수 있음), 둘 다 없으면 끝낸다.
 * 실행 잠금(단일 실행)은 호출자({@code IngestJobRunner})가 잡는다. 설계: docs/components/batch/IngestJob.md
 */
public class IngestJob {

    private static final Logger log = LoggerFactory.getLogger(IngestJob.class);

    private final TaskQueue queue;
    private final IngestRunStore runs;
    private final Discoverer discoverer;
    private final LawStore lawStore;
    private final Map<TaskKind, TaskHandler> handlers = new EnumMap<>(TaskKind.class);
    private final int workers;
    private final boolean indexEnabled;
    private final Duration idlePoll;
    private final Duration doneRetention;
    private final Clock clock;
    private final ObservationRegistry observations;
    private volatile boolean stopping;

    /**
     * @param indexEnabled false 면 자동 경로({@code run})가 INDEX 를 가져가지 않는다 — 수집은 계속, 색인은 큐에 남김.
     *                     직접 명령({@code index}·{@code reindex-all}·{@code sync})은 이 값과 무관
     */
    public IngestJob(TaskQueue queue, IngestRunStore runs, Discoverer discoverer, LawStore lawStore,
                     List<TaskHandler> handlers, int workers, boolean indexEnabled, Duration idlePoll,
                     Duration doneRetention, Clock clock, ObservationRegistry observations) {
        this.queue = queue;
        this.runs = runs;
        this.discoverer = discoverer;
        this.lawStore = lawStore;
        handlers.forEach(h -> this.handlers.put(h.kind(), h));
        for (TaskKind kind : TaskKind.values()) {
            if (!this.handlers.containsKey(kind)) {
                throw new IllegalArgumentException("핸들러 없음: " + kind);
            }
        }
        if (workers < 1) {
            throw new IllegalArgumentException("워커는 1개 이상: " + workers);
        }
        this.workers = workers;
        this.indexEnabled = indexEnabled;
        this.idlePoll = idlePoll;
        this.doneRetention = doneRetention;
        this.clock = clock;
        this.observations = observations == null ? ObservationRegistry.NOOP : observations;
    }

    /** 종료 신호 — 새 작업을 가져가지 않고, 진행 중 작업만 마치고 소진을 끝낸다. */
    public void requestStop() {
        stopping = true;
    }

    public RunReport run(JobCommand command) {
        return Observation.createNotStarted(Obs.BATCH_RUN, observations).observe(() -> execute(command));
    }

    private RunReport execute(JobCommand command) {
        // 정리 — 실행 잠금을 잡았으므로 열린 실행·RUNNING·미종료 시도는 모두 죽은 실행의 흔적이다
        int abandonedRuns = runs.closeAbandoned();
        int recovered = queue.recoverAbandoned();
        queue.purgeDoneBefore(OffsetDateTime.now(clock).minus(doneRetention));
        if (abandonedRuns > 0 || recovered > 0) {
            log.warn("[batch] 중단된 실행 {}건 닫음, 중단된 작업 {}건 복구", abandonedRuns, recovered);
        }

        long runId = runs.start(command.label());
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (recovered > 0) counts.put("recovered", recovered);
        try {
            Boolean listComplete = null;
            switch (command.type()) {
                case RUN -> {
                    DiscoveryResult discovery = discoverer.discover(command.full(), command.dryRun());
                    listComplete = discovery.listComplete();
                    counts.put("listed", discovery.listed());
                    counts.put("markedMissing", discovery.markedMissing());
                    counts.put("duplicateKeys", discovery.duplicateKeys());
                    discovery.planned().forEach((kind, n) -> counts.put("planned." + kind, n));
                    discovery.enqueued().forEach((kind, n) -> counts.put("enqueued." + kind, n));
                    if (command.dryRun()) {
                        return finish(runId, outcome(0, 0, listComplete), listComplete, counts);
                    }
                }
                case SYNC -> queue.enqueue(TaskKind.SYNC, command.lawId(), command.effectiveDate(), mstFor(command));
                case REINDEX_ALL -> {
                    int enqueued = 0;
                    for (PendingVersion version : lawStore.listPendingVersions()) {
                        if (queue.enqueue(TaskKind.INDEX, version.lawId(), version.effectiveDate(), null)) enqueued++;
                    }
                    counts.put("enqueued.INDEX", enqueued);
                }
                case REQUEUE_DEAD -> counts.put("requeued", queue.requeueDead());
                case INDEX -> { }   // 등록 없음 — 쌓여 있는 INDEX 만 소진
            }

            int[] drained = drain(runId, kindsFor(command));
            counts.put("done", drained[0]);
            counts.put("failed", drained[1]);
            counts.put("dead", drained[2]);
            counts.put("deferred.INDEX", queue.countReady(TaskKind.INDEX));   // 색인 지연 — 미룬 것은 결과에 넣지 않는다
            return finish(runId, outcome(drained[1], drained[2], listComplete), listComplete, counts);
        } catch (RuntimeException | Error e) {
            log.error("[batch] 실행 {} 실패", runId, e);
            runs.finish(runId, RunOutcome.FAILED, null, counts);   // SIGKILL 등으로 여기도 못 오면 다음 실행이 닫는다
            throw e;
        }
    }

    /**
     * 실행 결과 판정(IngestJob.md §실행 모델) — 이번 실행의 실패·격리 ≥1, 목록 미완주, 종료 신호로 멈춰
     * READY 가 남음 → PARTIAL. 이전 실행부터 있던 DEAD 는 따지지 않는다(별도 신호).
     */
    private RunOutcome outcome(int failed, int dead, Boolean listComplete) {
        boolean stoppedEarly = stopping && queue.countByStatus().containsKey(TaskStatus.READY);
        boolean partial = failed > 0 || dead > 0 || Boolean.FALSE.equals(listComplete) || stoppedEarly;
        return partial ? RunOutcome.PARTIAL : RunOutcome.SUCCESS;
    }

    /** 이번 실행이 처리하는 작업 종류 — 색인 꺼짐은 자동 경로(run)에만, index 명령은 INDEX 만. */
    private Set<TaskKind> kindsFor(JobCommand command) {
        return switch (command.type()) {
            case INDEX -> EnumSet.of(TaskKind.INDEX);
            case RUN -> indexEnabled ? EnumSet.allOf(TaskKind.class) : EnumSet.complementOf(EnumSet.of(TaskKind.INDEX));
            default -> EnumSet.allOf(TaskKind.class);
        };
    }

    /** sync 명령의 MST — 지정값, 없으면 저장된 시행예정본의 mst. 둘 다 없으면 거부. */
    private String mstFor(JobCommand command) {
        if (command.mst() != null) return command.mst();
        return lawStore.findPendingVersions(command.lawId()).stream()
                .filter(v -> v.effectiveDate().equals(command.effectiveDate()))
                .map(PendingVersion::mst)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "저장된 시행예정본이 없어 MST 를 알 수 없습니다 — sync <lawId> <efYd> <mst> 로 지정하세요."));
    }

    private RunReport finish(long runId, RunOutcome outcome, Boolean listComplete, Map<String, Integer> counts) {
        runs.finish(runId, outcome, listComplete, counts);
        log.info("[batch] 실행 {} {} — {}", runId, outcome, counts);
        return new RunReport(runId, outcome, Map.copyOf(counts));
    }

    /** 큐 소진({@code kinds} 종류만). 반환: {완료, 실패(다음 실행 재시도), 격리}. */
    private int[] drain(long runId, Set<TaskKind> kinds) {
        AtomicInteger done = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger dead = new AtomicInteger();
        Set<String> busy = ConcurrentHashMap.newKeySet();
        AtomicInteger inFlight = new AtomicInteger();
        Object claimLock = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> workLoop(runId, kinds, busy, inFlight, claimLock, done, failed, dead)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("소진 중 중단", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("워커 실패", e.getCause());
        } finally {
            pool.shutdownNow();
        }
        return new int[] {done.get(), failed.get(), dead.get()};
    }

    private void workLoop(long runId, Set<TaskKind> kinds, Set<String> busy, AtomicInteger inFlight, Object claimLock,
                          AtomicInteger done, AtomicInteger failed, AtomicInteger dead) {
        while (!stopping) {
            Optional<Task> claimed;
            synchronized (claimLock) {
                claimed = queue.claim(runId, Set.copyOf(busy), kinds);
                claimed.ifPresent(task -> {
                    busy.add(task.lawId());
                    inFlight.incrementAndGet();
                });
            }
            if (claimed.isEmpty()) {
                if (inFlight.get() == 0) return;
                sleep(idlePoll);
                continue;
            }
            Task task = claimed.get();
            try {
                Observation.createNotStarted(Obs.BATCH_TASK, observations)
                        .lowCardinalityKeyValue(Obs.TAG_KIND, task.kind().name())
                        .observe(() -> handlers.get(task.kind()).handle(task));
                if (queue.complete(task, runId)) {
                    done.incrementAndGet();
                    log.info("[batch] {} {}@{} 완료(시도 {})", task.kind(), task.lawId(), task.effectiveDate(),
                            task.attempts());
                } else {
                    log.warn("[batch] {} {}@{} 소유권 상실 — 완료를 반영하지 않음", task.kind(), task.lawId(),
                            task.effectiveDate());
                }
            } catch (RuntimeException e) {
                Optional<TaskStatus> status = queue.fail(task, runId, ErrorClassifier.isPermanent(e), e);
                if (status.isEmpty()) {
                    log.warn("[batch] {} {}@{} 소유권 상실 — 실패를 반영하지 않음: {}", task.kind(), task.lawId(),
                            task.effectiveDate(), e.toString());
                } else {
                    (status.get() == TaskStatus.DEAD ? dead : failed).incrementAndGet();
                    log.warn("[batch] {} {}@{} 실패(시도 {}) → {}: {}", task.kind(), task.lawId(),
                            task.effectiveDate(), task.attempts(), status.get(), e.toString());
                }
            } finally {
                busy.remove(task.lawId());
                inFlight.decrementAndGet();
            }
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대기 중 중단", e);
        }
    }
}
