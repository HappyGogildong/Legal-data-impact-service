package com.lia.core.pipeline.batch;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * 배치 명령(D62) — {@code run [--full] [--dry-run]} · {@code sync <lawId> <efYd> [mst]} · {@code index} ·
 * {@code reindex-all} · {@code requeue-dead}. Spring 의 {@code --spring.*} 옵션은 무시한다.
 */
public record JobCommand(Type type, boolean full, boolean dryRun, String lawId, LocalDate effectiveDate, String mst) {

    public enum Type { RUN, SYNC, INDEX, REINDEX_ALL, REQUEUE_DEAD }

    /**
     * @param nonOptionArgs 비옵션 인자 — 명령과 그 인자
     * @param optionNames   옵션 이름({@code --full} → {@code "full"})
     */
    public static JobCommand parse(List<String> nonOptionArgs, Set<String> optionNames) {
        if (nonOptionArgs.isEmpty()) {
            throw new IllegalArgumentException("명령이 필요합니다: run · sync <lawId> <efYd> [mst] · index · reindex-all · requeue-dead");
        }
        String name = nonOptionArgs.get(0);
        return switch (name) {
            case "run" -> new JobCommand(Type.RUN, optionNames.contains("full"), optionNames.contains("dry-run"),
                    null, null, null);
            case "sync" -> {
                if (nonOptionArgs.size() < 3) {
                    throw new IllegalArgumentException("사용법: sync <lawId> <efYd(yyyy-MM-dd)> [mst]");
                }
                yield new JobCommand(Type.SYNC, false, false, nonOptionArgs.get(1), date(nonOptionArgs.get(2)),
                        nonOptionArgs.size() > 3 ? nonOptionArgs.get(3) : null);
            }
            case "index" -> new JobCommand(Type.INDEX, false, false, null, null, null);
            case "reindex-all" -> new JobCommand(Type.REINDEX_ALL, false, false, null, null, null);
            case "requeue-dead" -> new JobCommand(Type.REQUEUE_DEAD, false, false, null, null, null);
            default -> throw new IllegalArgumentException("모르는 명령: " + name);
        };
    }

    /** ingest_runs.command 에 남길 표기. */
    public String label() {
        return switch (type) {
            case RUN -> "run" + (full ? " --full" : "") + (dryRun ? " --dry-run" : "");
            case SYNC -> "sync " + lawId + " " + effectiveDate + (mst == null ? "" : " " + mst);
            case INDEX -> "index";
            case REINDEX_ALL -> "reindex-all";
            case REQUEUE_DEAD -> "requeue-dead";
        };
    }

    private static LocalDate date(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시행일은 yyyy-MM-dd 형식이어야 합니다: " + text, e);
        }
    }
}
