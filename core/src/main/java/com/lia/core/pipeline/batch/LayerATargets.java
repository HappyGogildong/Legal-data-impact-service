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
        Set<QueryType> missing = EnumSet.noneOf(QueryType.class);
        if (summaries.findCurrent(law).isEmpty()) missing.add(QueryType.SUMMARY);
        // 기준선이 없는 개정본은 대조할 수 없다(데이터 이상) — 제정만 기준선 없이 전부 신설로 만든다(D42)
        boolean comparable = baseline != null || law.amendKind() == Law.AmendKind.제정;
        if (comparable && diffs.findCurrent(law, baseline).isEmpty()) missing.add(QueryType.DIFF);
        return missing;
    }
}
