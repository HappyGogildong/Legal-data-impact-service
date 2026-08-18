package com.lia.core.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lia.core.eval.GoldenSet.RetrievalCase;
import com.lia.core.eval.Retriever.Retrieved;

/**
 * 검색 평가 러너 — {@code (config + 골든셋 + Retriever)} → {@link EvalReport}.
 *
 * <p>각 케이스를 검색해 지표를 내고 케이스 평균을 취한다. 지표 컷오프는 고정(1·3·5)이라
 * config의 {@code topK}가 바뀌어도 baseline 키만 다를 뿐 지표 정의는 안정적이다(비교 재현성).
 * D33 통제변인: 같은 골든셋에 config 하나만 바꿔 돌린다.
 */
public class RagEvalRunner {

    private static final int[] KS = {1, 3, 5};

    private final Retriever retriever;

    public RagEvalRunner(Retriever retriever) {
        this.retriever = retriever;
    }

    public EvalReport run(RagConfig config, List<RetrievalCase> gold) {
        Map<String, Double> sum = new LinkedHashMap<>();
        for (RetrievalCase c : gold) {
            List<String> ranked = retriever.retrieve(c.query(), config).stream()
                    .map(Retrieved::sourceId).toList();
            Set<String> relevant = Set.copyOf(c.expectedSourceIds());

            for (int k : KS) {
                add(sum, "recall@" + k, RetrievalMetrics.recallAtK(ranked, relevant, k));
                add(sum, "hit@" + k, RetrievalMetrics.hitAtK(ranked, relevant, k));
            }
            add(sum, "mrr", RetrievalMetrics.reciprocalRank(ranked, relevant));
            add(sum, "ndcg@5", RetrievalMetrics.ndcgAtK(ranked, relevant, 5));
        }

        int n = Math.max(1, gold.size());
        Map<String, Double> avg = new LinkedHashMap<>();
        sum.forEach((key, total) -> avg.put(key, total / n));
        return new EvalReport(config, avg);
    }

    private static void add(Map<String, Double> acc, String key, double v) {
        acc.merge(key, v, Double::sum);
    }
}
