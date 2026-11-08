package com.lia.core.eval;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.lia.core.pipeline.plan.QueryType;

/**
 * 결과 묶음 → 지표와 분모(D83). 순수 함수. 지표 정의: docs/components/eval/OfflineEvaluator.md.
 *
 * <p>분모가 0인 지표는 싣지 않는다 — 없는 값을 0이나 1로 꾸미면 기준선 비교가 거짓말을 한다.
 */
public final class OfflineMetrics {

    public static final String LAW_RESOLUTION = "law.resolution";
    public static final String DISCOVERY_HIT_AT_3 = "discovery.hit@3";
    public static final String DISCOVERY_MRR = "discovery.mrr";
    public static final String DISCOVERY_IRRELEVANT = "discovery.irrelevant";
    public static final String ARTICLE_MATCH = "article.match";
    public static final String NO_MATCH_CANDIDATE_RATE = "nomatch.candidate_rate";
    public static final String REFUSAL_RECALL = "refusal.recall";
    public static final String REFUSAL_OVER = "refusal.over";
    public static final String INTENT_MACRO_F1 = "intent.macro_f1";

    /** 낮을수록 좋은 지표 — 회귀 게이트가 오르는 쪽을 실패로 본다. */
    public static final Set<String> LOWER_IS_BETTER =
            Set.of(DISCOVERY_IRRELEVANT, NO_MATCH_CANDIDATE_RATE, REFUSAL_OVER);

    private static final int HIT_CUTOFF = 3;

    private OfflineMetrics() {}

    public static Map<String, Measure> score(List<EvalCase> cases, List<CaseOutcome> outcomes) {
        if (cases.size() != outcomes.size()) {
            throw new IllegalArgumentException(
                    "문항 %d개와 결과 %d개의 수가 다르다".formatted(cases.size(), outcomes.size()));
        }
        Ratio resolution = new Ratio();
        Ratio hit = new Ratio();
        double reciprocalRankSum = 0;
        Ratio irrelevant = new Ratio();
        Ratio article = new Ratio();
        Ratio noMatch = new Ratio();
        Ratio refusal = new Ratio();
        Ratio overRefusal = new Ratio();
        List<QueryType[]> intents = new ArrayList<>();   // [정답, 예측]

        for (int i = 0; i < cases.size(); i++) {
            EvalCase c = cases.get(i);
            CaseOutcome o = outcomes.get(i);
            switch (c.kind()) {
                case ANSWERABLE -> {
                    Set<String> expected = Set.copyOf(c.expectedLawIds());
                    overRefusal.add(o.refused());
                    if (c.lawNamed()) {
                        resolution.add(o.resolved() && expected.contains(o.candidateLawIds().get(0)));
                    } else {
                        hit.add(RetrievalMetrics.hitAtK(o.candidateLawIds(), expected, HIT_CUTOFF) == 1.0);
                        reciprocalRankSum += RetrievalMetrics.reciprocalRank(o.candidateLawIds(), expected);
                        Set<String> allowed = new HashSet<>(expected);
                        allowed.addAll(c.acceptableLawIds());
                        o.candidateLawIds().forEach(id -> irrelevant.add(!allowed.contains(id)));
                    }
                }
                case NO_MATCH -> noMatch.add(!o.candidateLawIds().isEmpty());
                case REFUSE -> refusal.add(o.refused());
            }
            if (c.articleNo() != null) article.add(Objects.equals(c.articleNo(), o.articleNo()));
            if (c.expectedDimension() != null) intents.add(new QueryType[] {c.expectedDimension(), o.primaryType()});
        }

        Map<String, Measure> out = new LinkedHashMap<>();
        resolution.putInto(out, LAW_RESOLUTION);
        hit.putInto(out, DISCOVERY_HIT_AT_3);
        if (hit.total > 0) out.put(DISCOVERY_MRR, new Measure(reciprocalRankSum / hit.total, hit.total));
        irrelevant.putInto(out, DISCOVERY_IRRELEVANT);
        article.putInto(out, ARTICLE_MATCH);
        noMatch.putInto(out, NO_MATCH_CANDIDATE_RATE);
        refusal.putInto(out, REFUSAL_RECALL);
        overRefusal.putInto(out, REFUSAL_OVER);
        intent(intents, out);
        return out;
    }

    /**
     * 의도 분류 — 차원별 정밀도(분모 = 그 차원으로 예측한 수)·재현율(분모 = 그 차원이 정답인 수)·F1,
     * 정답에 나온 차원들의 macro F1. 예측이 없는 차원의 정밀도는 정의되지 않으므로 싣지 않고, F1 은 0이다.
     */
    private static void intent(List<QueryType[]> pairs, Map<String, Measure> out) {
        if (pairs.isEmpty()) return;
        Map<QueryType, int[]> counts = new EnumMap<>(QueryType.class);   // [TP, FP, FN]
        for (QueryType[] pair : pairs) {
            QueryType expected = pair[0];
            QueryType predicted = pair[1];
            if (expected == predicted) {
                counts.computeIfAbsent(expected, k -> new int[3])[0]++;
                continue;
            }
            counts.computeIfAbsent(expected, k -> new int[3])[2]++;
            if (predicted != null) counts.computeIfAbsent(predicted, k -> new int[3])[1]++;
        }
        double f1Sum = 0;
        int dimensions = 0;
        for (var e : counts.entrySet()) {
            String prefix = "intent." + e.getKey().name() + ".";
            int tp = e.getValue()[0];
            int fp = e.getValue()[1];
            int fn = e.getValue()[2];
            if (tp + fp > 0) out.put(prefix + "precision", new Measure((double) tp / (tp + fp), tp + fp));
            if (tp + fn > 0) {
                double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
                double recall = (double) tp / (tp + fn);
                double f1 = tp == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
                out.put(prefix + "recall", new Measure(recall, tp + fn));
                out.put(prefix + "f1", new Measure(f1, tp + fn));
                f1Sum += f1;
                dimensions++;
            }
        }
        out.put(INTENT_MACRO_F1, new Measure(f1Sum / dimensions, pairs.size()));
    }

    /** 참인 비율과 분모. */
    private static final class Ratio {
        private int hits;
        private int total;

        void add(boolean hit) {
            total++;
            if (hit) hits++;
        }

        void putInto(Map<String, Measure> out, String key) {
            if (total > 0) out.put(key, new Measure((double) hits / total, total));
        }
    }
}
