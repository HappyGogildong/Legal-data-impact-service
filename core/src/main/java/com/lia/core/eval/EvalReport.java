package com.lia.core.eval;

import java.util.Map;

/** 오프라인 평가 보고서(D83) — RED 단계 껍데기. */
public record EvalReport(RagConfig config, long indexedLaws, int cases, Map<String, Measure> metrics) {

    public String toJson() {
        return "{}";
    }

    public static EvalReport fromJson(String json) {
        throw new UnsupportedOperationException("아직 구현되지 않았다");
    }
}
