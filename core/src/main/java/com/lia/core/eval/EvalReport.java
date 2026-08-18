package com.lia.core.eval;

import java.util.Map;
import java.util.TreeMap;

/**
 * 한 config에 대한 평가 결과 — 지표 이름 → 값. baseline 비교·회귀 게이트의 입력.
 */
public record EvalReport(RagConfig config, Map<String, Double> metrics) {

    public EvalReport {
        metrics = new TreeMap<>(metrics);   // 안정 순서(출력·비교 재현성)
    }

    public double metric(String name) {
        return metrics.getOrDefault(name, Double.NaN);
    }
}
