package com.lia.core.pipeline.plan;

import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * 계획 결과 — {@code Planned}(검증된 실행 질의) | {@code Unresolved}(4상태 거부, fail-closed).
 * sealed라 소비자(dispatcher/컨트롤러)가 두 경우를 빠짐없이 처리한다.
 */
public sealed interface PlanResult permits PlanResult.Planned, PlanResult.Unresolved {

    record Planned(AnalysisQuery query) implements PlanResult {}

    /** 미해소·비법령 — 지어내지 않는다. {@link ResolutionResult}의 4상태·안내를 그대로 전달. */
    record Unresolved(ResolutionResult resolution) implements PlanResult {}
}
