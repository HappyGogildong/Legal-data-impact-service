package com.lia.core.pipeline.batch;

import java.util.Optional;

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
 */
public class IngestJobRunner implements ApplicationRunner, ExitCodeGenerator, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(IngestJobRunner.class);

    private final IngestJob job;
    private final DataSource dataSource;
    private volatile int exitCode = 0;

    public IngestJobRunner(IngestJob job, DataSource dataSource) {
        this.job = job;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        JobCommand command;
        try {
            command = JobCommand.parse(args.getNonOptionArgs(), args.getOptionNames());
        } catch (IllegalArgumentException e) {
            log.error("[batch] {}", e.getMessage());
            exitCode = 1;
            return;
        }
        Optional<JobLock> lock = JobLock.tryAcquire(dataSource);
        if (lock.isEmpty()) {
            log.info("[batch] 이미 다른 실행이 진행 중 — 종료");
            exitCode = 0;
            return;
        }
        try (JobLock held = lock.get()) {
            exitCode = job.run(command).exitCode();
        } catch (RuntimeException e) {
            log.error("[batch] 실행 실패", e);
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    /** 종료 신호(SIGTERM)로 컨텍스트가 닫히면 새 작업을 가져가지 않게 한다. */
    @Override
    public void destroy() {
        job.requestStop();
    }
}
