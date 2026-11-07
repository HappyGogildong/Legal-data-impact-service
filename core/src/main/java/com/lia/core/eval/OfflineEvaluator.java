package com.lia.core.eval;

import java.util.List;

import com.lia.core.pipeline.discover.LawDiscovery;
import com.lia.core.pipeline.plan.QueryPlanner;

/** 질문 1건을 서비스 경로로 돌린다(D83) — RED 단계 껍데기. */
public class OfflineEvaluator {

    private final QueryPlanner planner;
    private final LawDiscovery discovery;

    public OfflineEvaluator(QueryPlanner planner, LawDiscovery discovery) {
        this.planner = planner;
        this.discovery = discovery;
    }

    public CaseOutcome evaluate(String query) {
        return new CaseOutcome(false, List.of(), false, null, null);
    }
}
