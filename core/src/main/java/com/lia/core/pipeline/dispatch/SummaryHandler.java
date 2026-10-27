package com.lia.core.pipeline.dispatch;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawSummaries;

/**
 * SUMMARY 차원(Layer A, 프로필 무관) — 개정 요약. 미리 만든 쉬운 요약(D67)이 유효하면(시행예정본·인용이 지금 근거 안) 그것으로 답하고(추론 없음),
 * 없거나 낡았거나 시행예정이 아니면 정본을 SUMMARY 로 AnalysisEngine 에 위임한다. 저장본의 근거 집합은 ContextBuilder 로 다시 조립한다
 * (결정론 — 인용 칩의 근거 해석에 쓴다). 실시간 결과는 저장하지 않는다(쓰기는 배치만).
 * 요약은 비교가 아니므로 기준선은 쓰지 않는다(ContextBuilder가 SUMMARY에선 baseline 블록 생략).
 */
@Component
public class SummaryHandler implements DimensionHandler {

    private final AnalysisEngine engine;
    private final LawSummaries summaries;
    private final ContextBuilder contextBuilder;

    public SummaryHandler(AnalysisEngine engine, LawSummaries summaries, ContextBuilder contextBuilder) {
        this.engine = engine;
        this.summaries = summaries;
        this.contextBuilder = contextBuilder;
    }

    @Override public QueryType type() { return QueryType.SUMMARY; }

    @Override public AnalyzeResponse handle(DispatchContext ctx) {
        AnalyzeRequest request = new AnalyzeRequest(QueryType.SUMMARY, ctx.law(), null);
        // 시행예정본만 저장본을 쓴다 — 상세 화면(LawController)과 같은 기준
        Optional<ImpactResult> stored = ctx.law().pending() ? summaries.findCurrent(ctx.law()) : Optional.empty();
        if (stored.isPresent()) {
            return new AnalyzeResponse(stored.get(), contextBuilder.build(request).injectedSourceIds());
        }
        return engine.analyze(request);
    }
}
