package com.lia.core.pipeline.batch;

import java.util.Map;

/** 실행 한 번의 결과 — 실행 기록과 같은 건수. */
public record RunReport(long runId, RunOutcome outcome, Map<String, Integer> counts) {

    public int exitCode() {
        return outcome.exitCode();
    }
}
