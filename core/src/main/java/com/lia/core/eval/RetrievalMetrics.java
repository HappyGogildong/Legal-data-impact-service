package com.lia.core.eval;

import java.util.List;
import java.util.Set;

/**
 * 순위 지표 — <b>결정론·순수 함수</b>(D83).
 *
 * <p>모두 한 문항에 대한 값이다. 문항 여러 개의 집계는 {@link OfflineMetrics}가 낸다.
 * {@code ranked}는 1순위부터 정렬된 후보 ID(법령ID) 목록, {@code relevant}는 정답 집합.
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {}

    /** Hit@k — 상위 k에 정답이 하나라도 있으면 1.0. */
    public static double hitAtK(List<String> ranked, Set<String> relevant, int k) {
        return topK(ranked, k).stream().anyMatch(relevant::contains) ? 1.0 : 0.0;
    }

    /** Reciprocal Rank — 첫 정답 순위의 역수(없으면 0). 문항 평균이 MRR. */
    public static double reciprocalRank(List<String> ranked, Set<String> relevant) {
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private static List<String> topK(List<String> ranked, int k) {
        return ranked.subList(0, Math.min(k, ranked.size()));
    }
}
