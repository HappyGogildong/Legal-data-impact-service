package com.lia.core.pipeline.resolve;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 토큰 기반 유사도(0~100) — rapidfuzz token_set_ratio 의 경량 대체.
 * 동일 제목=100, 부분 포함은 교집합/합집합(Jaccard) 기반으로 근사한다.
 * (임계값 의미는 Python 참조 구현과 동일하게 유지: confident=88, ambiguousMin=60)
 */
public final class TokenSimilarity {

    private TokenSimilarity() {}

    public static double ratio(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        if (ta.equals(tb)) return 100.0;

        Set<String> inter = new LinkedHashSet<>(ta);
        inter.retainAll(tb);
        // token_set_ratio 성질 근사: 한쪽이 다른 쪽을 포함하면 높은 점수
        double containment = (double) inter.size() / Math.min(ta.size(), tb.size());
        Set<String> union = new LinkedHashSet<>(ta);
        union.addAll(tb);
        double jaccard = (double) inter.size() / union.size();
        return Math.max(containment * 0.9, jaccard) * 100.0;
    }

    private static Set<String> tokens(String s) {
        if (s == null) return Set.of();
        return new LinkedHashSet<>(Arrays.stream(s.trim().split("\\s+"))
                .filter(t -> !t.isBlank())
                .toList());
    }
}
