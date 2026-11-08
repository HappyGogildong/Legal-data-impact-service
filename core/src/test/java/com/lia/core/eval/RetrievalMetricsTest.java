package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색 지표 수학 검증 — 손으로 계산한 값과 대조(합성 랭킹, RAG 불필요).
 */
class RetrievalMetricsTest {

    private static final List<String> RANKED = List.of("a", "b", "c", "d", "e");

    @Test
    @DisplayName("Hit@k — 상위 k에 정답이 있으면 1")
    void hitAtK() {
        Set<String> rel = Set.of("c");   // rank 3
        assertEquals(0.0, RetrievalMetrics.hitAtK(RANKED, rel, 1));
        assertEquals(1.0, RetrievalMetrics.hitAtK(RANKED, rel, 3));
        assertEquals(1.0, RetrievalMetrics.hitAtK(RANKED, rel, 5));
    }

    @Test
    @DisplayName("Reciprocal Rank — 첫 정답 순위의 역수")
    void reciprocalRank() {
        assertEquals(1.0 / 3, RetrievalMetrics.reciprocalRank(RANKED, Set.of("c")), 1e-9);
        assertEquals(1.0, RetrievalMetrics.reciprocalRank(RANKED, Set.of("a", "c")), 1e-9);
        assertEquals(0.0, RetrievalMetrics.reciprocalRank(RANKED, Set.of("z")), 1e-9);
    }

    @Test
    @DisplayName("경계 — 정답 없음·빈 순위·k가 목록보다 큼")
    void boundaries() {
        assertEquals(0.0, RetrievalMetrics.hitAtK(RANKED, Set.of("z"), 5));
        assertEquals(0.0, RetrievalMetrics.hitAtK(List.of(), Set.of("a"), 3));
        assertEquals(1.0, RetrievalMetrics.hitAtK(RANKED, Set.of("e"), 10));
    }
}
