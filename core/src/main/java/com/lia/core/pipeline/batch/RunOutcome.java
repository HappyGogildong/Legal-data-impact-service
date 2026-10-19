package com.lia.core.pipeline.batch;

/** 실행 결과 — 종료 코드 SUCCESS=0 · FAILED=1 · PARTIAL=2. 판정 기준: IngestJob.md §실행 모델. */
public enum RunOutcome {
    SUCCESS(0), FAILED(1), PARTIAL(2);

    private final int exitCode;

    RunOutcome(int exitCode) { this.exitCode = exitCode; }

    public int exitCode() { return exitCode; }
}
