package com.lia.core.eval;

import java.util.List;
import java.util.Set;

/**
 * 검색 정확도 지표 — <b>결정론·순수 함수</b>(회귀 게이트, D33).
 *
 * <p>모두 한 케이스에 대한 값이다. 케이스 여러 개의 평균은 {@link RagEvalRunner}가 낸다.
 * {@code ranked}는 rank 1(가장 유사)부터 정렬된 인용키 목록, {@code relevant}는 정답 집합.
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {}

    /** Hit@k — 상위 k에 정답이 하나라도 있으면 1.0. */
    public static double hitAtK(List<String> ranked, Set<String> relevant, int k) {
        return topK(ranked, k).stream().anyMatch(relevant::contains) ? 1.0 : 0.0;
    }

    /** Recall@k — 정답 중 상위 k에 든 비율. */
    public static double recallAtK(List<String> ranked, Set<String> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        long found = topK(ranked, k).stream().filter(relevant::contains).distinct().count();
        return (double) found / relevant.size();
    }

    /** Reciprocal Rank — 첫 정답 순위의 역수(없으면 0). 케이스 평균이 MRR. */
    public static double reciprocalRank(List<String> ranked, Set<String> relevant) {
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /** nDCG@k — 이진 적합도(정답=1). 순위 가중 정확도. */
    public static double ndcgAtK(List<String> ranked, Set<String> relevant, int k) {
        List<String> top = topK(ranked, k);
        double dcg = 0.0;
        for (int i = 0; i < top.size(); i++) {
            if (relevant.contains(top.get(i))) {
                dcg += 1.0 / log2(i + 2);   // rank i(0-based) → 분모 log2(i+2)
            }
        }
        int ideal = Math.min(k, relevant.size());
        double idcg = 0.0;
        for (int i = 0; i < ideal; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private static List<String> topK(List<String> ranked, int k) {
        return ranked.subList(0, Math.min(k, ranked.size()));
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
