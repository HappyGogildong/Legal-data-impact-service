package com.lia.core.pipeline.dispatch;

import org.springframework.stereotype.Component;

import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.QueryType;

/**
 * SUMMARY 차원(Layer A, 프로필 무관) — 개정 요약. 정본을 SUMMARY로 AnalysisEngine에 위임한다.
 * 요약은 비교가 아니므로 기준선은 쓰지 않는다(ContextBuilder가 SUMMARY에선 baseline 블록 생략).
 */
@Component
public class SummaryHandler implements DimensionHandler {

    private final AnalysisEngine engine;

    public SummaryHandler(AnalysisEngine engine) { this.engine = engine; }

    @Override public QueryType type() { return QueryType.SUMMARY; }

    @Override public AnalyzeResponse handle(DispatchContext ctx) {
        return engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, ctx.law(), null));
    }
}
