package com.lia.core.pipeline.dispatch;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawSummaries;

/**
 * SUMMARY 차원(Layer A, 프로필 무관) — 개정 요약. 배치가 미리 만든 쉬운 요약(D67)이 유효하면(시행예정본·인용이 지금 근거 안)
 * 그것으로 답하고, 없으면 Opus 를 부르지 않고 {@link NotPrecomputedException} 으로 미충족을 알린다(D80).
 * 저장본 응답의 근거 집합은 정본으로 다시 조립해 엔진 응답과 모양을 맞춘다(결정론·외부 호출 없음).
 * 요약은 비교가 아니므로 기준선은 쓰지 않는다(ContextBuilder가 SUMMARY에선 baseline 블록 생략).
 * {@code live} 는 로컬 개발 전용 — 저장본이 없을 때 AnalysisEngine 에 위임한다.
 */
@Component
public class SummaryHandler implements DimensionHandler {

    private final AnalysisEngine engine;
    private final LawSummaries summaries;
    private final ContextBuilder contextBuilder;
    private final boolean live;

    public SummaryHandler(AnalysisEngine engine, LawSummaries summaries, ContextBuilder contextBuilder,
                          @Value("${lia.analysis.layer-a-live}") boolean live) {
        this.engine = engine;
        this.summaries = summaries;
        this.contextBuilder = contextBuilder;
        this.live = live;
    }

    @Override public QueryType type() { return QueryType.SUMMARY; }

    @Override public AnalyzeResponse handle(DispatchContext ctx) {
        AnalyzeRequest request = new AnalyzeRequest(QueryType.SUMMARY, ctx.law(), null);
        // 시행예정본만 저장본을 쓴다 — 상세 화면(LawController)과 같은 기준
        Optional<ImpactResult> stored = ctx.law().pending() ? summaries.findCurrent(ctx.law()) : Optional.empty();
        if (stored.isPresent()) {
            return new AnalyzeResponse(stored.get(), contextBuilder.build(request).injectedSourceIds());
        }
        if (live) return engine.analyze(request);
        throw new NotPrecomputedException("쉬운 요약 저장본 없음: " + ctx.law().ref());
    }
}
