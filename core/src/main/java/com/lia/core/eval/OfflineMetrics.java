package com.lia.core.eval;

import java.util.List;
import java.util.Map;

/** 결과 묶음 → 지표(D83) — RED 단계 껍데기. */
public final class OfflineMetrics {

    private OfflineMetrics() {}

    public static Map<String, Measure> score(List<EvalCase> cases, List<CaseOutcome> outcomes) {
        return Map.of();
    }
}
