package com.lia.core.pipeline.plan;

import java.util.Set;

/**
 * 자연어 질의를 실행 가능한 <b>타입 DTO</b>로 표현(D46) — 번역 이후 실행 경로는 이 타입이 고정한다.
 * 우리가 산출한 판정이므로 <b>생성자가 불변식을 강제</b>한다(fail-closed 승계, D23 방식).
 *
 * <p>entities(lawName·articleNo·keywords 등)는 별도 필드로 두지 않는다 — {@code target}
 * ({@link Target.Reference}의 LawRef · {@link Target.Discovery}의 DiscoveryCriteria)에 이미 담긴다.
 */
public record AnalysisQuery(
        QueryType primaryType,        // FE 주 뷰·주 검색
        Set<QueryType> types,         // 채울 차원(포괄질문=복수)
        Target target,
        String intentSummary,         // 알고 싶은 것 한 줄
        ArticleScope articleScope,
        boolean profileBound,         // Layer B 채울 수 있는지
        Options options
) {
    public AnalysisQuery {
        if (primaryType == null) throw new IllegalArgumentException("primaryType 필수.");
        if (types == null || types.isEmpty()) throw new IllegalArgumentException("types는 비어 있을 수 없다.");
        types = Set.copyOf(types);
        if (!types.contains(primaryType)) {
            throw new IllegalArgumentException("primaryType은 types에 포함되어야 한다: " + primaryType + " ∉ " + types);
        }
        if (target == null) throw new IllegalArgumentException("target 필수 — 미해소가 분석으로 새면 안 된다.");
        if (articleScope == null) articleScope = ArticleScope.CHANGED_ONLY;
        if (options == null) options = Options.defaults();
    }

    public record Options(String language, String promptVersion) {
        public static Options defaults() { return new Options("ko", "0.1"); }
    }
}
