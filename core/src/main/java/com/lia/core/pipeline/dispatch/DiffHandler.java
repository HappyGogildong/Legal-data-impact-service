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
import com.lia.core.store.LawDiffs;

/**
 * DIFF 차원(Layer A, 프로필 무관) — 변경 조문 ↔ 시행중 기준선 대조. 배치가 미리 만든 설명이 유효하면(시행예정본·정본과 기준선
 * revision·인용이 지금 근거 안) 그것으로 답하고, 없으면 Opus 를 부르지 않고 {@link NotPrecomputedException} 으로 미충족을 알린다(D80).
 * 기준선이 null이면 제정(전부 신설) — ContextBuilder가 그대로 처리(D42). {@code live} 는 로컬 개발 전용.
 * 질문이 시행예정본의 조문을 지목하면 {@link ArticleFocus} 로 그 조문의 답만 남긴다(D82).
 */
@Component
public class DiffHandler implements DimensionHandler {

    private final AnalysisEngine engine;
    private final LawDiffs diffs;
    private final ContextBuilder contextBuilder;
    private final boolean live;

    public DiffHandler(AnalysisEngine engine, LawDiffs diffs, ContextBuilder contextBuilder,
                       @Value("${lia.analysis.layer-a-live}") boolean live) {
        this.engine = engine;
        this.diffs = diffs;
        this.contextBuilder = contextBuilder;
        this.live = live;
    }

    @Override public QueryType type() { return QueryType.DIFF; }

    /** 대조 대상 시행중 기준선이 필요하다(개정본 한정 — 제정은 dispatcher가 예외 처리). */
    @Override public boolean needsBaseline() { return true; }

    @Override public AnalyzeResponse handle(DispatchContext ctx) {
        String articleNo = ctx.law().pending() ? ctx.articleNo() : null;
        if (articleNo == null) return wholeLaw(ctx);

        ArticleFocus.requireChanged(ctx.law(), ctx.baseline(), articleNo);
        AnalyzeResponse response = wholeLaw(ctx);
        return new AnalyzeResponse(ArticleFocus.narrow(response.result(), ctx.law(), ctx.baseline(), articleNo),
                response.injectedSourceIds());
    }

    private AnalyzeResponse wholeLaw(DispatchContext ctx) {
        AnalyzeRequest request = new AnalyzeRequest(QueryType.DIFF, ctx.law(), ctx.baseline());
        Optional<ImpactResult> stored = ctx.law().pending() ? diffs.findCurrent(ctx.law(), ctx.baseline()) : Optional.empty();
        if (stored.isPresent()) {
            return new AnalyzeResponse(stored.get(), contextBuilder.build(request).injectedSourceIds());
        }
        if (live) return engine.analyze(request);
        throw new NotPrecomputedException("변경점 저장본 없음: " + ctx.law().ref());
    }
}
