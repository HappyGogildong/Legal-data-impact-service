package com.lia.core.eval;

import java.util.List;
import java.util.function.Function;

import com.lia.core.eval.GoldenSet.RefusalCase;
import com.lia.core.pipeline.resolve.ResolutionState;

/**
 * 거부 정확도 — <b>fail-closed 안전 게이트</b>(D23·D08).
 *
 * <p>답할 수 없는 질의(조작·이미 시행 중·비법령, S8/S9)에 대해 시스템이 <b>지어내지 않고
 * 거부</b>하는지 측정한다. 법률 서비스에선 "환각 안 함 = 정답"이고, 이는 결정론이다.
 * {@code RESOLVED}가 나오면 곧 미해소 입력이 분석으로 샌 것이라 실패다.
 *
 * <p><b>RAG 없이 지금 가동</b> — {@code SourceAnalyzer.resolve}만 있으면 된다.
 */
public final class RefusalMetric {

    private RefusalMetric() {}

    /**
     * 올바르게 거부했는가. 미등록({@code NOT_FOUND_YET})과 허위 의심({@code UNVERIFIED})은
     * 안내가 달라야 하므로(D23) <b>정확한 상태 일치</b>를 요구한다.
     */
    public static boolean correct(ResolutionState actual, ResolutionState expected) {
        return actual == expected;
    }

    /** 거부 상태인가(RESOLVED가 아님) — 안전성의 최소 조건. */
    public static boolean refused(ResolutionState state) {
        return state != ResolutionState.RESOLVED;
    }

    /**
     * 케이스 집합의 거부 정확도(0~1). {@code resolver}는 질의 → 해소 상태
     * (실제 {@code SourceAnalyzer::resolve} 결과의 {@code state()} 또는 페이크).
     */
    public static double accuracy(List<RefusalCase> cases, Function<String, ResolutionState> resolver) {
        if (cases.isEmpty()) {
            return 1.0;
        }
        long ok = cases.stream().filter(c -> correct(resolver.apply(c.query()), c.expectedState())).count();
        return (double) ok / cases.size();
    }
}
