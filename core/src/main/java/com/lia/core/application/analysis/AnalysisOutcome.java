package com.lia.core.application.analysis;

import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * 온라인 분석 유스케이스 결과 — {@code Analyzed}(해소·디스패치 완료) | {@code Unresolved}(4상태 거부, fail-closed).
 * sealed라 소비자(web 매퍼)가 두 경우를 빠짐없이 처리한다. <b>프레임워크·HTTP 타입 비의존</b>(application 계층).
 */
public sealed interface AnalysisOutcome permits AnalysisOutcome.Analyzed, AnalysisOutcome.Unresolved {

    record Analyzed(AnalysisQuery query, DispatchResult result) implements AnalysisOutcome {}

    record Unresolved(ResolutionResult resolution) implements AnalysisOutcome {}
}
