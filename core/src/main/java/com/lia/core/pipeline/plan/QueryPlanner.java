package com.lia.core.pipeline.plan;

import java.util.EnumSet;
import java.util.List;

import com.lia.core.pipeline.connector.RawLaw;
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
            List<RawLaw> alternatives = List.of();
            if (explicitRef != null) {
                ref = explicitRef;                         // 이미 특정됨 — 해소 생략
            } else {
                ResolutionResult rr = sourceAnalyzer.resolve(draft.lawName());
                if (!rr.analyzable()) return new PlanResult.Unresolved(rr);
                ref = new LawRef(rr.resolved().lawId(), rr.resolved().effectiveDate(), draft.articleNo());
                alternatives = rr.alternatives();          // 같은 법령의 다른 시행예정본(D43)
            }
            return planned(draft, new Target.Reference(ref), profilePresent, alternatives);
        }

        // Discovery — criteria 구성만(검색 실행은 dispatch, 이번 범위 밖)
        DiscoveryCriteria criteria = new DiscoveryCriteria(
                draft.keywords(), draft.conditions(), draft.domains(), profilePresent);
        return planned(draft, new Target.Discovery(criteria), profilePresent, List.of());
    }

    private static PlanResult planned(AnalysisQueryDraft draft, Target target, boolean profilePresent,
                                      List<RawLaw> alternatives) {
        if (target instanceof Target.Discovery) {
            // 발견은 후보만 돌려준다(D70) — 분석 차원은 후보를 고른 재요청(explicitRef)에서 같은 질문으로 다시 정한다.
            AnalysisQuery discovery = new AnalysisQuery(QueryType.LOOKUP, EnumSet.of(QueryType.LOOKUP), target,
                    draft.intentSummary(), ArticleScope.CHANGED_ONLY, false, AnalysisQuery.Options.defaults());
            return new PlanResult.Planned(discovery, alternatives);
        }
        // Layer B 는 프로필이 없어도 빼지 않는다 — 디스패처가 사유(PROFILE_REQUIRED 등)를 unmet 으로 실어
        // 화면이 로그인·프로필 안내를 띄우게 한다(D69). profileBound 는 "채울 수 있는가"만 뜻한다.
        EnumSet<QueryType> types = draft.types().isEmpty()
                ? EnumSet.noneOf(QueryType.class) : EnumSet.copyOf(draft.types());
        if (types.isEmpty()) {
            // 분류 실패면 기본 차원으로 — 거부하지 않는다.
            types.add(QueryType.SUMMARY);
        }

        // 프로필이 없고 Layer A 차원이 하나도 없으면 SUMMARY 를 더한다 — 선계산이라 비용이 0이고(D67)
        // 익명 첫 질문이 로그인 안내만 남기지 않게 한다. Layer B 차원은 그대로 둬 미충족 카드가 로그인을 이끈다.
        if (!profilePresent && types.stream().allMatch(QueryType::isLayerB)) {
            types.add(QueryType.SUMMARY);
        }

        QueryType primary = types.contains(draft.primaryType()) ? draft.primaryType() : types.iterator().next();
        boolean profileBound = profilePresent && types.stream().anyMatch(QueryType::isLayerB);

        AnalysisQuery q = new AnalysisQuery(primary, types, target,
                draft.intentSummary(), ArticleScope.CHANGED_ONLY, profileBound, AnalysisQuery.Options.defaults());
        return new PlanResult.Planned(q, alternatives);
    }
}
