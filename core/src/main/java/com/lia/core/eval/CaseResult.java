package com.lia.core.eval;

import java.util.List;

import com.lia.core.eval.EvalCase.Kind;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 보고서의 문항별 결과 한 행(D83) — 지표 합계만으로는 어느 문항이 왜 틀렸는지 알 수 없어서 싣는다.
 * 정답 세트 문항이라 실제 사용자 질문은 없다.
 *
 * @param passed 이 문항이 지표 판정을 통과했는가({@link OfflineMetrics#passed})
 */
public record CaseResult(String query, Kind kind, boolean lawNamed, List<String> expectedLawIds, String expectedArticleNo,
                         QueryType expectedDimension, boolean passed, String route, String lawName,
                         List<String> candidateLawIds, boolean refused, String articleNo, QueryType primaryType) {

    public CaseResult {
        expectedLawIds = expectedLawIds == null ? List.of() : List.copyOf(expectedLawIds);
        candidateLawIds = candidateLawIds == null ? List.of() : List.copyOf(candidateLawIds);
    }

    public static CaseResult of(EvalCase c, CaseOutcome o) {
        return new CaseResult(c.query(), c.kind(), c.lawNamed(), c.expectedLawIds(), c.articleNo(), c.expectedDimension(),
                OfflineMetrics.passed(c, o), o.route(), o.lawName(), o.candidateLawIds(), o.refused(),
                o.articleNo(), o.primaryType());
    }
}
