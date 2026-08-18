package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.resolve.ResolutionState;
import com.lia.core.pipeline.resolve.SourceAnalyzer;

/**
 * 거부(fail-closed) 게이트 검증 — <b>RAG 없이 실제 SourceAnalyzer로 가동</b>.
 */
class RefusalMetricTest {

    // 매칭 없음(빈 조회) → 답할 수 없는 질의는 4상태의 거부로 떨어진다.
    private final SourceAnalyzer sa = new SourceAnalyzer((q, n) -> List.of());

    @Test
    @DisplayName("골든셋의 답할 수 없는 질의는 전부 올바르게 거부한다")
    void 거부_정확도_1() {
        var cases = GoldenSet.refusal("eval/refusal-gold.json");
        double acc = RefusalMetric.accuracy(cases, q -> sa.resolve(q).state());
        assertEquals(1.0, acc, "미등록·비법령은 지어내지 않고 정확한 상태로 거부해야 한다(D23)");
    }

    @Test
    @DisplayName("RESOLVED가 나오면 안전 위반")
    void resolved는_거부_실패() {
        assertFalse(RefusalMetric.correct(ResolutionState.RESOLVED, ResolutionState.NOT_FOUND_YET));
        assertFalse(RefusalMetric.refused(ResolutionState.RESOLVED));
        assertTrue(RefusalMetric.refused(ResolutionState.UNVERIFIED));
        assertTrue(RefusalMetric.refused(ResolutionState.NOT_FOUND_YET));
    }
}
