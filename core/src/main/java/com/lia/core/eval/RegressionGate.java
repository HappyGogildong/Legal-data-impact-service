package com.lia.core.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * 회귀 게이트 — config 변경이 <b>기존 질문셋 성능을 떨어뜨리면 실패</b>시킨다.
 *
 * <p>두 검사:
 * <ul>
 *   <li><b>상대(회귀):</b> baseline 대비 {@code tolerance}보다 더 떨어진 지표.</li>
 *   <li><b>절대(임계):</b> 최소 기준선 미달 지표 — 예 {@code recall@5 ≥ 0.80}(D33),
 *       {@code refusalAccuracy = 1.0}·{@code faithfulness = 1.0}(안전 게이트).</li>
 * </ul>
 * 판정에 LLM을 쓰지 않는다(D14) — 전부 결정론 수치 비교다.
 */
public class RegressionGate {

    /** 절대 최소 임계. */
    public record Threshold(String metric, double min) {}

    public record Result(boolean passed, List<String> failures) {}

    private final double tolerance;
    private final List<Threshold> absoluteMins;

    public RegressionGate(double tolerance, List<Threshold> absoluteMins) {
        this.tolerance = tolerance;
        this.absoluteMins = List.copyOf(absoluteMins);
    }

    /** baseline 대비 회귀 + 절대 임계를 함께 검사. baseline이 null이면 절대 임계만. */
    public Result check(EvalReport report, EvalReport baseline) {
        List<String> failures = new ArrayList<>();

        if (baseline != null) {
            for (var e : baseline.metrics().entrySet()) {
                double now = report.metric(e.getKey());
                double was = e.getValue();
                if (!Double.isNaN(now) && now < was - tolerance) {
                    failures.add("회귀 %s: %.3f → %.3f (Δ%.3f, 허용 %.3f)"
                            .formatted(e.getKey(), was, now, now - was, tolerance));
                }
            }
        }

        for (Threshold t : absoluteMins) {
            double now = report.metric(t.metric());
            if (Double.isNaN(now) || now < t.min()) {
                failures.add("임계 미달 %s: %.3f < %.3f".formatted(t.metric(), now, t.min()));
            }
        }

        return new Result(failures.isEmpty(), failures);
    }
}
