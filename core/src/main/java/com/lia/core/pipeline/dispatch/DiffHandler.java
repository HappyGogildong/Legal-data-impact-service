package com.lia.core.pipeline.dispatch;

import org.springframework.stereotype.Component;

import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.QueryType;

/**
 * DIFF 차원(Layer A, 프로필 무관) — 변경 조문 ↔ 시행중 기준선 대조. 정본+기준선을 DIFF로
 * AnalysisEngine에 위임한다. 기준선이 null이면 제정(전부 신설) — ContextBuilder가 그대로 처리(D42).
 */
@Component
public class DiffHandler implements DimensionHandler {

    private final AnalysisEngine engine;

    public DiffHandler(AnalysisEngine engine) { this.engine = engine; }

    @Override public QueryType type() { return QueryType.DIFF; }

    /** 대조 대상 시행중 기준선이 필요하다(개정본 한정 — 제정은 dispatcher가 예외 처리). */
    @Override public boolean needsBaseline() { return true; }

    @Override public AnalyzeResponse handle(DispatchContext ctx) {
        return engine.analyze(new AnalyzeRequest(QueryType.DIFF, ctx.law(), ctx.baseline()));
    }
}
