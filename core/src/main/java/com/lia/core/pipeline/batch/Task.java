package com.lia.core.pipeline.batch;

import java.time.LocalDate;

/**
 * 큐에서 가져간 작업. {@code attemptId}는 이번 시도의 ingest_task_attempts 행, {@code mst}는 SYNC 전용,
 * {@code attempts}는 이번 시도를 포함한 횟수.
 */
public record Task(long taskId, long attemptId, TaskKind kind, String lawId, LocalDate effectiveDate, String mst,
                   int attempts) {
}
