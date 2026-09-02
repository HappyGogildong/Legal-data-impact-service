package com.lia.core.pipeline.analyze;

import java.util.List;
import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.eval.FaithfulnessGate;

/**
 * 해석 오케스트레이터(얇음) — {@code 조립 → 추론 → 인용검증 → 재생성(≤N) → 폴백}(D37 명시적 워크플로).
 * 검색 없음(정합화) — {@link ContextBuilder}가 정확 조회된 정본에서 context를 조립한다.
 *
 * <p>스펙: docs/components/AnalysisEngine.md · docs/prompts/analysis-prompt-spec.md.
 */
public class AnalysisEngine {

    private final ContextBuilder contextBuilder;
    private final Reasoner reasoner;
    private final int maxAttempts;

    public AnalysisEngine(ContextBuilder contextBuilder, Reasoner reasoner, int maxAttempts) {
        this.contextBuilder = contextBuilder;
        this.reasoner = reasoner;
        this.maxAttempts = maxAttempts;
    }

    public AnalyzeResponse analyze(AnalyzeRequest req) {
        AnalysisContext ctx = contextBuilder.build(req);
        Set<String> injected = ctx.injectedSourceIds();

        // 조립된 근거로 추론 → 1차 인용검증. 실패 시 재생성(≤N), 끝까지 실패면 근거부족 폴백(환각 미노출).
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ImpactResult result = reasoner.reason(ctx);
            if (grounded(result, injected)) {
                return new AnalyzeResponse(result, injected);
            }
        }
        throw new InsufficientGroundingException(
                "인용 검증 %d회 실패 — 근거 부족(환각 노출 금지)".formatted(maxAttempts));
    }

    /** 인용 존재성(§6.2) — 모든 주장이 주입 source_id만 인용. eval {@link FaithfulnessGate} 규칙 재사용. */
    private static boolean grounded(ImpactResult r, Set<String> injectedSourceIds) {
        List<FaithfulnessGate.Claim> claims = r.claims().stream()
                .map(c -> new FaithfulnessGate.Claim(c.statement(), c.citations()))
                .toList();
        return FaithfulnessGate.passes(claims, injectedSourceIds);
    }
}
