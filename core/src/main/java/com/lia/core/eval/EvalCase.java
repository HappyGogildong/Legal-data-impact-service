package com.lia.core.eval;

import java.util.List;

import com.lia.core.pipeline.plan.QueryType;

/** 정답 세트 1문항(D83) — RED 단계 껍데기. */
public record EvalCase(String query, Kind kind, boolean lawNamed, List<String> expectedLawIds,
                       List<String> acceptableLawIds, String articleNo, QueryType expectedDimension) {

    public enum Kind { ANSWERABLE, NO_MATCH, REFUSE }
}
