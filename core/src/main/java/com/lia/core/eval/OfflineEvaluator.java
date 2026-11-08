package com.lia.core.eval;

import java.util.List;
import java.util.Optional;

import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.discover.DiscoveredLaw;
import com.lia.core.pipeline.discover.LawDiscovery;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.PlanResult;
import com.lia.core.pipeline.plan.QueryPlanner;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;
import com.lia.core.pipeline.resolve.ResolutionState;

/**
 * 질문 1건을 서비스와 같은 경로로 돌려 {@link CaseOutcome} 으로 요약한다(D83).
 * 평가 전용 검색·해소를 두지 않는다 — 서비스의 {@link QueryPlanner}·{@link LawDiscovery} 빈을 그대로 쓴다.
 *
 * <p>프로필 없이, 명시적 법령 없이 묻는다(익명 첫 질문과 같다). 스펙: docs/components/eval/OfflineEvaluator.md.
 */
public class OfflineEvaluator {

    private final QueryPlanner planner;
    private final LawDiscovery discovery;

    public OfflineEvaluator(QueryPlanner planner, LawDiscovery discovery) {
        this.planner = planner;
        this.discovery = discovery;
    }

    public CaseOutcome evaluate(String query) {
        return switch (planner.plan(query, null, false)) {
            case PlanResult.Unresolved unresolved -> unresolved(unresolved.resolution());
            case PlanResult.Planned planned -> planned(query, planned.query());
        };
    }

    /** 미등록·비법령은 거부, 해소 후보가 여럿이면 후보(사용자가 고른다 — 거부가 아니다). */
    private static CaseOutcome unresolved(ResolutionResult resolution) {
        if (resolution.state() == ResolutionState.AMBIGUOUS) {
            return new CaseOutcome(false, lawIds(resolution.candidates()), false, null, null);
        }
        return new CaseOutcome(true, List.of(), false, null, null);
    }

    private CaseOutcome planned(String query, AnalysisQuery analysis) {
        return switch (analysis.target()) {
            case Target.Reference reference -> {
                LawRef ref = reference.lawRef();
                yield new CaseOutcome(false, List.of(ref.lawId()), true, ref.articleNo(), analysis.primaryType());
            }
            case Target.Discovery ignored -> {
                List<String> candidates = discovery.discover(query, Optional.empty()).candidates().stream()
                        .map(DiscoveredLaw::lawId)
                        .toList();
                yield new CaseOutcome(false, candidates, false, null, analysis.primaryType());
            }
        };
    }

    private static List<String> lawIds(List<RawLaw> laws) {
        return laws.stream().map(RawLaw::lawId).toList();
    }
}
