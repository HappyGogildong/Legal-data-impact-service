package com.lia.core.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 회귀 게이트 — 기준선 대비 <b>나빠진 방향</b>으로 허용 오차를 넘은 지표가 있으면 실패(D83).
 * 판정에 LLM 을 쓰지 않는다(D14) — 수치 비교뿐이다.
 *
 * <p>허용 오차는 설정값과 {@code 1/분모} 중 큰 값이다. 30문항이면 한 문항이 약 3.3%p 라, 더 작은 오차는
 * 우연한 한 문항으로 실패한다. 기준선에 없는 지표는 비교하지 않고, 기준선에 있던 지표가 사라지면 실패다
 * (정답 세트나 경로가 바뀌어 분모가 0이 된 것을 조용히 넘기지 않는다).
 */
public class RegressionGate {

    /** 부동소수 비교 여유 — 정확히 허용 오차만큼 변한 값을 실패로 보지 않게. */
    private static final double EPSILON = 1e-9;

    public record Result(boolean passed, List<String> failures) {
        public Result {
            failures = List.copyOf(failures);
        }
    }

    private final double tolerance;
    private final Set<String> lowerIsBetter;

    /**
     * @param tolerance     허용 오차(비율, 0~1)
     * @param lowerIsBetter 낮을수록 좋은 지표 키 — 나머지는 높을수록 좋다
     */
    public RegressionGate(double tolerance, Set<String> lowerIsBetter) {
        this.tolerance = tolerance;
        this.lowerIsBetter = Set.copyOf(lowerIsBetter);
    }

    public Result check(EvalReport report, EvalReport baseline) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Measure> entry : baseline.metrics().entrySet()) {
            String key = entry.getKey();
            Measure was = entry.getValue();
            Measure now = report.metrics().get(key);
            if (now == null) {
                failures.add("지표 없음 %s: 기준선 %.3f (n=%d)".formatted(key, was.value(), was.n()));
                continue;
            }
            double allowed = Math.max(tolerance, 1.0 / Math.max(1, now.n())) + EPSILON;
            double worse = lowerIsBetter.contains(key) ? now.value() - was.value() : was.value() - now.value();
            if (worse > allowed) {
                failures.add("회귀 %s: %.3f (n=%d) → %.3f (n=%d), 허용 %.3f"
                        .formatted(key, was.value(), was.n(), now.value(), now.n(), allowed - EPSILON));
            }
        }
        return new Result(failures.isEmpty(), failures);
    }
}
