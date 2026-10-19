package com.lia.core.pipeline.batch;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 작업 큐 — {@code ingest_tasks}(현재 상태) + {@code ingest_task_attempts}(시도 이력), D62.
 *
 * <ul>
 *   <li>등록은 중복 방지(같은 종류·법령 버전의 READY·RUNNING·DEAD 는 하나).</li>
 *   <li>가져가기는 {@code FOR UPDATE SKIP LOCKED} + 시도 행 INSERT 를 한 문장으로.</li>
 *   <li>완료·실패는 {@code last_attempt_run}(실행 토큰)으로 소유권을 확인한다 — 복구 후 늦게 복귀한
 *       옛 작업자는 큐 상태를 덮지 못한다.</li>
 *   <li>실행당 1회 시도, 일시적 오류는 {@link #MAX_ATTEMPTS}회면 DEAD.</li>
 * </ul>
 * 모든 쓰기가 한 문장(데이터 변경 CTE)이라 별도 트랜잭션이 필요 없다. 스키마: {@code V5__ingest_batch.sql}.
 */
public class TaskQueue {

    public static final int MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final JdbcClient jdbc;

    public TaskQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 등록. 같은 (종류, 법령 버전)의 READY·RUNNING·DEAD 작업이 있으면 무시하고 false. */
    public boolean enqueue(TaskKind kind, String lawId, LocalDate effectiveDate, String mst) {
        return jdbc.sql("""
                INSERT INTO ingest_tasks (kind, law_id, effective_date, mst, status, created_at, updated_at)
                VALUES (:kind, :lawId, :efYd, :mst, 'READY', now(), now())
                ON CONFLICT (kind, law_id, effective_date) WHERE status IN ('READY', 'RUNNING', 'DEAD') DO NOTHING
                """)
                .param("kind", kind.name())
                .param("lawId", lawId)
                .param("efYd", effectiveDate)
                .param("mst", mst)
                .update() == 1;
    }

    /**
     * 다음 작업을 RUNNING 으로 가져가고 시도 행을 만든다(한 문장). 이번 실행에서 이미 시도한 작업과
     * 처리 중인 법령({@code busyLawIds})은 제외하고, {@code kinds}(이번 실행이 처리하는 종류 — 색인을 끄면
     * INDEX 제외, index 명령은 INDEX 만) 중에서 우선순위(TRANSITION·PURGE → SYNC → INDEX) 순. 없으면 empty.
     */
    public Optional<Task> claim(long runId, Set<String> busyLawIds, Set<TaskKind> kinds) {
        return jdbc.sql("""
                WITH claimed AS (
                    UPDATE ingest_tasks
                    SET status = 'RUNNING', attempts = attempts + 1, last_attempt_run = :runId, updated_at = now()
                    WHERE task_id = (
                        SELECT task_id FROM ingest_tasks
                        WHERE status = 'READY'
                          AND last_attempt_run IS DISTINCT FROM :runId
                          AND law_id <> ALL(CAST(:busy AS text[]))
                          AND kind = ANY(CAST(:kinds AS text[]))
                        ORDER BY CASE kind WHEN 'TRANSITION' THEN 0 WHEN 'PURGE' THEN 0 WHEN 'SYNC' THEN 1 ELSE 2 END,
                                 task_id
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED)
                    RETURNING task_id, kind, law_id, effective_date, mst, attempts),
                attempt AS (
                    INSERT INTO ingest_task_attempts (task_id, run_id, attempt, started_at)
                    SELECT task_id, :runId, attempts, now() FROM claimed
                    RETURNING attempt_id, task_id)
                SELECT c.task_id, a.attempt_id, c.kind, c.law_id, c.effective_date, c.mst, c.attempts
                FROM claimed c JOIN attempt a USING (task_id)
                """)
                .param("runId", runId)
                .param("busy", busyLawIds.toArray(String[]::new))
                .param("kinds", kinds.stream().map(Enum::name).toArray(String[]::new))
                .query((rs, rowNum) -> new Task(rs.getLong("task_id"), rs.getLong("attempt_id"),
                        TaskKind.valueOf(rs.getString("kind")), rs.getString("law_id"),
                        rs.getObject("effective_date", LocalDate.class), rs.getString("mst"), rs.getInt("attempts")))
                .optional();
    }

    /** 그 종류의 READY 작업 수 — 미룬 색인(deferred.INDEX) 집계. */
    public int countReady(TaskKind kind) {
        return jdbc.sql("SELECT count(*) FROM ingest_tasks WHERE status = 'READY' AND kind = :kind")
                .param("kind", kind.name())
                .query(Integer.class)
                .single();
    }

    /** 완료 기록(작업 DONE + 시도 행 DONE). 이 실행이 소유하지 않으면 아무것도 쓰지 않고 false. */
    public boolean complete(Task task, long runId) {
        return jdbc.sql("""
                WITH done AS (
                    UPDATE ingest_tasks SET status = 'DONE', last_error = NULL, updated_at = now()
                    WHERE task_id = :taskId AND status = 'RUNNING' AND last_attempt_run = :runId
                    RETURNING task_id)
                UPDATE ingest_task_attempts SET finished_at = now(), outcome = 'DONE'
                WHERE attempt_id = :attemptId AND EXISTS (SELECT 1 FROM done)
                """)
                .param("taskId", task.taskId())
                .param("runId", runId)
                .param("attemptId", task.attemptId())
                .update() == 1;
    }

    /**
     * 실패 기록. 영구 오류이거나 시도 횟수가 한도에 닿으면 DEAD, 아니면 READY(다음 실행에서 재시도).
     * 시도 행에는 RETRY/DEAD 와 오류 종류·메시지. 이 실행이 소유하지 않으면 아무것도 쓰지 않고 empty.
     */
    public Optional<TaskStatus> fail(Task task, long runId, boolean permanent, Throwable error) {
        String message = truncate(error.toString());
        return jdbc.sql("""
                WITH failed AS (
                    UPDATE ingest_tasks
                    SET status = CASE WHEN :permanent OR attempts >= :max THEN 'DEAD' ELSE 'READY' END,
                        last_error = :message, updated_at = now()
                    WHERE task_id = :taskId AND status = 'RUNNING' AND last_attempt_run = :runId
                    RETURNING status),
                logged AS (
                    UPDATE ingest_task_attempts
                    SET finished_at = now(),
                        outcome = CASE (SELECT status FROM failed) WHEN 'DEAD' THEN 'DEAD' ELSE 'RETRY' END,
                        error_type = :errorType, error_message = :message
                    WHERE attempt_id = :attemptId AND EXISTS (SELECT 1 FROM failed))
                SELECT status FROM failed
                """)
                .param("permanent", permanent)
                .param("max", MAX_ATTEMPTS)
                .param("message", message)
                .param("errorType", (permanent ? "PERMANENT:" : "TRANSIENT:") + error.getClass().getSimpleName())
                .param("taskId", task.taskId())
                .param("runId", runId)
                .param("attemptId", task.attemptId())
                .query(String.class)
                .optional()
                .map(TaskStatus::valueOf);
    }

    /**
     * 죽은 실행이 남긴 흔적 복구 — 끝나지 못한 시도 행은 ABANDONED, RUNNING 작업은 READY(시도 한도면 DEAD).
     * 실행 잠금을 잡은 뒤에만 부른다(그 시점의 RUNNING 은 모두 죽은 프로세스의 것). 복구한 작업 수.
     */
    public int recoverAbandoned() {
        return jdbc.sql("""
                WITH abandoned AS (
                    UPDATE ingest_task_attempts
                    SET finished_at = now(), outcome = 'ABANDONED', error_type = 'ABANDONED'
                    WHERE finished_at IS NULL)
                UPDATE ingest_tasks
                SET status = CASE WHEN attempts >= :max THEN 'DEAD' ELSE 'READY' END,
                    last_error = '중단된 시도 — 프로세스가 작업을 끝내지 못함', updated_at = now()
                WHERE status = 'RUNNING'
                """)
                .param("max", MAX_ATTEMPTS)
                .update();
    }

    /** 기준 시각보다 오래된 DONE 삭제(보존 기간 정리, 시도 행은 FK CASCADE). */
    public int purgeDoneBefore(OffsetDateTime cutoff) {
        return jdbc.sql("DELETE FROM ingest_tasks WHERE status = 'DONE' AND updated_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
    }

    /** DEAD → READY — 같은 행(task_id·시도 이력 유지), 시도 횟수 초기화. */
    public int requeueDead() {
        return jdbc.sql("""
                UPDATE ingest_tasks SET status = 'READY', attempts = 0, last_attempt_run = NULL, updated_at = now()
                WHERE status = 'DEAD'
                """)
                .update();
    }

    /** 상태별 개수 — 없는 상태는 키가 없다. */
    public Map<TaskStatus, Integer> countByStatus() {
        Map<TaskStatus, Integer> counts = new EnumMap<>(TaskStatus.class);
        jdbc.sql("SELECT status, count(*) AS n FROM ingest_tasks GROUP BY status")
                .query((rs, rowNum) -> Map.entry(TaskStatus.valueOf(rs.getString("status")), rs.getInt("n")))
                .list()
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
