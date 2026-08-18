package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.eval.FaithfulnessGate.Claim;

/**
 * 결정론 Faithfulness(인용 존재성) 게이트 검증 — §6.2.
 */
class FaithfulnessGateTest {

    private static final Set<String> INJECTED =
            Set.of("LAW:001809@2026-08-04:art:18", "LAW:001809@2026-08-04:art:104");

    private static Claim claim(String stmt, String... cites) {
        return new Claim(stmt, List.of(cites));
    }

    @Test
    @DisplayName("주입 컨텍스트의 인용만 있으면 지지")
    void supported() {
        assertTrue(FaithfulnessGate.supported(
                claim("통합심의 요청 가능", "LAW:001809@2026-08-04:art:18"), INJECTED));
    }

    @Test
    @DisplayName("환각 인용·무인용은 지지 안 됨")
    void unsupported() {
        assertFalse(FaithfulnessGate.supported(
                claim("있지도 않은 조문", "LAW:001809@2026-08-04:art:999"), INJECTED), "환각 인용");
        assertFalse(FaithfulnessGate.supported(
                claim("근거 없는 주장"), INJECTED), "무인용");
    }

    @Test
    @DisplayName("Faithfulness = 지지되는 주장 비율, 게이트는 전부 지지해야 통과")
    void faithfulnessAndGate() {
        List<Claim> claims = List.of(
                claim("ok", "LAW:001809@2026-08-04:art:18"),      // 지지
                claim("hallucinated", "LAW:001809@2026-08-04:art:999"),  // 환각
                claim("no-cite"));                                 // 무인용

        assertEquals(1.0 / 3, FaithfulnessGate.faithfulness(claims, INJECTED), 1e-9);
        assertFalse(FaithfulnessGate.passes(claims, INJECTED), "하나라도 위반이면 게이트 실패");
        assertEquals(2, FaithfulnessGate.violations(claims, INJECTED).size());

        assertTrue(FaithfulnessGate.passes(
                List.of(claim("ok", "LAW:001809@2026-08-04:art:104")), INJECTED));
    }
}
