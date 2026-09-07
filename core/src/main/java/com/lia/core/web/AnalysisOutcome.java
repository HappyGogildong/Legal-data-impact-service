package com.lia.core.web;

import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * 온라인 분석 결과 — {@code Analyzed}(해소·디스패치 완료) | {@code Unresolved}(4상태 거부, fail-closed).
 * sealed라 컨트롤러가 두 경우를 빠짐없이 매핑한다. 웹 프레임워크 타입에 의존하지 않는다.
 */
public sealed interface AnalysisOutcome permits AnalysisOutcome.Analyzed, AnalysisOutcome.Unresolved {

    record Analyzed(AnalysisQuery query, DispatchResult result) implements AnalysisOutcome {}

    record Unresolved(ResolutionResult resolution) implements AnalysisOutcome {}
}
