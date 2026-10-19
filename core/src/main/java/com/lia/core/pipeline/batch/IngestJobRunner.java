package com.lia.core.pipeline.batch;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

/**
 * 배치 진입점(D62, {@code batch} 프로파일) — 명령 해석 → 실행 잠금 → 잡 실행 → 종료 코드.
 * 종료 코드는 {@code LiaCoreApplication}이 {@code SpringApplication.exit}로 프로세스에 돌려준다.
 * 0=성공(또는 다른 실행이 이미 진행 중) · 1=실행 실패 · 2=부분 실패.
 *
 * <p>종료 신호(SIGTERM): 컨텍스트가 닫히며 {@link #destroy()}가 불리면 잡에 멈춤을 알리고 실행이 끝나
 * 기록될 때까지(최대 {@link #SHUTDOWN_WAIT}) 기다린다. 이 빈이 의존하는 {@code IngestJob}·{@code DataSource}는
 * 이 빈보다 나중에 파괴되므로 기다리는 동안 DB 연결은 열려 있다.
 */
public class IngestJobRunner implements ApplicationRunner, ExitCodeGenerator, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(IngestJobRunner.class);

    /** 종료 훅이 실행 종료를 기다리는 최대 시간 — compose {@code stop_grace_period}(60초)보다 짧게. */
    static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(50);

    private final IngestJob job;
    private final DataSource dataSource;
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Object lifecycle = new Object();
    private boolean started;
    private boolean closing;
    private volatile int exitCode = 0;

    public IngestJobRunner(IngestJob job, DataSource dataSource) {
        this.job = job;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            synchronized (lifecycle) {
                if (closing) {
                    log.warn("[batch] 종료 중 — 실행하지 않음");
                    return;
                }
                started = true;
            }
            exitCode = execute(args);
        } finally {
            finished.countDown();   // 모든 경로(명령 오류·잠금 경합 포함) — 종료 훅의 대기를 푼다
        }
    }

    private int execute(ApplicationArguments args) {
        JobCommand command;
        try {
            command = JobCommand.parse(args.getNonOptionArgs(), args.getOptionNames());
        } catch (IllegalArgumentException e) {
            log.error("[batch] {}", e.getMessage());
            return 1;
        }
        Optional<JobLock> lock = JobLock.tryAcquire(dataSource);
        if (lock.isEmpty()) {
            log.info("[batch] 이미 다른 실행이 진행 중 — 종료");
            return 0;
        }
        int result;
        try {
            result = job.run(command).exitCode();
        } catch (RuntimeException e) {
            log.error("[batch] 실행 실패", e);
            result = 1;
        } finally {
            unlock(lock.get());
        }
        return result;
    }

    /** 잠금 해제 실패는 잡의 결과(종료 코드)를 바꾸지 않는다 — 프로세스가 끝나면 커넥션과 함께 풀린다. */
    private static void unlock(JobLock lock) {
        try {
            lock.close();
        } catch (RuntimeException e) {
            log.error("[batch] 잠금 해제 실패 — 프로세스 종료 시 커넥션과 함께 풀린다", e);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    /**
     * 종료 신호(SIGTERM)로 컨텍스트가 닫히면 새 작업을 가져가지 않게 하고, 진행 중 작업이 끝나 실행이
     * 기록될 때까지 기다린다. 실행이 시작되지 않았으면(시작 전 종료) 기다릴 것이 없다.
     */
    @Override
    public void destroy() {
        synchronized (lifecycle) {
            closing = true;
            if (!started) return;
        }
        job.requestStop();
        try {
            if (!finished.await(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("[batch] 실행이 {} 안에 끝나지 않음 — 기다리지 않고 종료(다음 실행이 정리)", SHUTDOWN_WAIT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[batch] 실행 종료 대기 중 중단됨");
        }
    }
}
