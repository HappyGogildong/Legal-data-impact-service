package com.lia.core.pipeline.batch;

import java.util.EnumSet;
import java.util.Set;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawDiffs;
import com.lia.core.store.LawSummaries;

/** 시행예정본 하나에 아직 없는 Layer A 차원(D80) — 작업 처리와 {@code summarize --dry-run} 이 같은 기준을 쓴다. */
final class LayerATargets {

    private LayerATargets() {}

    static Set<QueryType> missing(Law law, Law baseline, LawSummaries summaries, LawDiffs diffs) {
        return EnumSet.noneOf(QueryType.class);
    }
}
