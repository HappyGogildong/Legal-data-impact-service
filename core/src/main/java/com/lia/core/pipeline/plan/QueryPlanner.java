package com.lia.core.pipeline.plan;

import java.util.EnumSet;
import java.util.List;

import com.lia.core.pipeline.plan.AnalysisQueryDraft.TargetKind;
import com.lia.core.pipeline.resolve.ResolutionResult;
import com.lia.core.pipeline.resolve.SourceAnalyzer;

/**
 * 자연어 → {@link PlanResult}. LLM 번역은 {@link QueryTranslator}에 위임하고, 이후는 <b>결정론</b>:
 * 해소 분기·프로필 게이팅·fail-closed(D46). 실행(dispatch)은 이 범위 밖(AnalysisEngine 후속).
 *
 * <p>스펙: docs/components/QueryPlanner.md.
 */
public class QueryPlanner {

    private final QueryTranslator translator;
    private final SourceAnalyzer sourceAnalyzer;

    public QueryPlanner(QueryTranslator translator, SourceAnalyzer sourceAnalyzer) {
        this.translator = translator;
        this.sourceAnalyzer = sourceAnalyzer;
    }

    public PlanResult plan(String query, LawRef explicitRef, boolean profilePresent) {
        AnalysisQueryDraft draft = translator.translate(query);

        // 비법령·오프토픽 조기 거부(fail-closed) — 번역기의 법령의도 신호
        if (!draft.isLawQuery()) {
            return new PlanResult.Unresolved(
                    ResolutionResult.unverified(List.of(), "법령에 관한 질의가 아닙니다."));
        }

        boolean reference = explicitRef != null || draft.targetKind() == TargetKind.REFERENCE;
        if (reference) {
            LawRef ref;
            if (explicitRef != null) {
                ref = explicitRef;                         // 이미 특정됨 — 해소 생략
            } else {
                ResolutionResult rr = sourceAnalyzer.resolve(draft.lawName());
                if (!rr.analyzable()) return new PlanResult.Unresolved(rr);
                ref = new LawRef(rr.resolved().lawId(), rr.resolved().effectiveDate(), draft.articleNo());
            }
            return planned(draft, new Target.Reference(ref), profilePresent);
        }

        // Discovery — criteria 구성만(검색 실행은 dispatch, 이번 범위 밖)
        DiscoveryCriteria criteria = new DiscoveryCriteria(
                draft.keywords(), draft.conditions(), draft.domains(), profilePresent);
        return planned(draft, new Target.Discovery(criteria), profilePresent);
    }

    private static PlanResult planned(AnalysisQueryDraft draft, Target target, boolean profilePresent) {
        // 프로필 게이팅 — 프로필 없으면 Layer B(IMPACT·ACTION) 제거(best-effort, 거부 아님).
        EnumSet<QueryType> types = draft.types().isEmpty()
                ? EnumSet.noneOf(QueryType.class) : EnumSet.copyOf(draft.types());
        if (!profilePresent) types.removeIf(QueryType::isLayerB);
        if (types.isEmpty()) {
            // 다 걸러졌으면 기본 차원으로 강등 — 분류 실패로 거부하지 않는다.
            types.add(target instanceof Target.Discovery ? QueryType.LOOKUP : QueryType.SUMMARY);
        }

        // primaryType이 제거됐으면 남은 것 중 결정론적으로(EnumSet=ordinal 순) 선택.
        QueryType primary = types.contains(draft.primaryType()) ? draft.primaryType() : types.iterator().next();
        boolean profileBound = types.stream().anyMatch(QueryType::isLayerB);

        AnalysisQuery q = new AnalysisQuery(primary, types, target,
                draft.intentSummary(), ArticleScope.CHANGED_ONLY, profileBound, AnalysisQuery.Options.defaults());
        return new PlanResult.Planned(q);
    }
}
