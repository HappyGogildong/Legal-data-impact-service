package com.lia.core.pipeline.analyze;

import java.util.HashSet;
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

    /**
     * 인용 존재성(§6.2) — 결과의 <b>모든</b> 인용 source_id가 주입 컨텍스트에만 실재해야 한다.
     * 실패·스키마불일치는 예외가 아니라 {@code false}(→재생성/폴백)로 흘려 환각을 노출하지 않는다.
     *
     * <p>① {@code claims}: 누락(null)·공백은 근거 없는 결과이므로 실패(NPE·공허통과 방지).
     * 각 주장은 ≥1 인용 + 모두 실재(eval {@link FaithfulnessGate} 규칙 재사용).
     * ② {@code impacts}·{@code actions}가 인용한 source_id도 모두 실재해야 한다 — 환각 인용이
     * 이 필드로 새는 것을 차단(DIFF는 impacts를 산출한다, §5).
     *
     * <p>현재 Layer A(SUMMARY·DIFF)는 claims를 필수로 본다. Layer B(IMPACT·ACTION) 착지 시
     * 차원별 필수 필드로 정교화한다.
     */
    private static boolean grounded(ImpactResult r, Set<String> injectedSourceIds) {
        if (r == null || r.claims() == null || r.claims().isEmpty()) {
            return false;   // 스키마 불일치(누락)·무근거 → 재생성 대상
        }
        List<FaithfulnessGate.Claim> claims = r.claims().stream()
                .map(c -> new FaithfulnessGate.Claim(c.statement(), c.citations()))
                .toList();
        if (!FaithfulnessGate.passes(claims, injectedSourceIds)) {
            return false;
        }
        return injectedSourceIds.containsAll(citedSourceIds(r));
    }

    /** 결과가 인용한 모든 source_id 수집(claims·impacts·actions.basis) — null 안전. */
    private static Set<String> citedSourceIds(ImpactResult r) {
        Set<String> ids = new HashSet<>();
        if (r.claims() != null) {
            r.claims().forEach(c -> { if (c.citations() != null) ids.addAll(c.citations()); });
        }
        if (r.impacts() != null) {
            r.impacts().forEach(i -> { if (i.citations() != null) ids.addAll(i.citations()); });
        }
        if (r.actions() != null) {
            r.actions().forEach(a -> { if (a.basis() != null) ids.addAll(a.basis()); });
        }
        return ids;
    }
}
