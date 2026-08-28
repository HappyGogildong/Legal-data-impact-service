package com.lia.core.pipeline.plan;

import java.util.List;
import java.util.Set;

/**
 * {@link QueryTranslator}의 LLM 출력(Haiku) — 자연어를 구조화한 <b>초안</b>. 아직 해소 전이라
 * 플래너가 이걸 받아 해소·게이팅해 {@link AnalysisQuery}로 확정한다.
 *
 * <p>{@code isLawQuery} = 법령 관련 의도 신호. false면(오프토픽 S9) 플래너가 조기 거부(UNVERIFIED).
 */
public record AnalysisQueryDraft(
        QueryType primaryType,
        Set<QueryType> types,
        boolean isLawQuery,
        TargetKind targetKind,
        String lawName,               // REFERENCE 지목(있으면)
        String articleNo,
        List<String> keywords,        // DISCOVERY 조건
        List<String> conditions,
        List<String> domains,
        String intentSummary
) {
    public enum TargetKind { REFERENCE, DISCOVERY }

    public AnalysisQueryDraft {
        types = types == null ? Set.of() : Set.copyOf(types);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        domains = domains == null ? List.of() : List.copyOf(domains);
    }
}
