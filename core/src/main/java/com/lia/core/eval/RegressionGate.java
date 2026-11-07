package com.lia.core.eval;

import java.util.List;
import java.util.Set;

/** 회귀 게이트(D83) — RED 단계 껍데기. */
public class RegressionGate {

    public record Result(boolean passed, List<String> failures) {}

    public RegressionGate(double tolerance, Set<String> lowerIsBetter) {}

    public Result check(EvalReport report, EvalReport baseline) {
        return new Result(true, List.of());
    }
}
