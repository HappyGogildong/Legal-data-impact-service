package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.eval.RegressionGate.Threshold;

/**
 * 회귀 게이트 — baseline 대비 하락·절대 임계 미달을 결정론으로 실패시킨다.
 */
class RegressionGateTest {

    private static EvalReport report(double recall5) {
        return new EvalReport(RagConfig.defaults(), Map.of("recall@5", recall5));
    }

    @Test
    @DisplayName("baseline 대비 허용오차 초과 하락 = 회귀 실패")
    void 회귀_실패() {
        var res = new RegressionGate(0.02, List.of()).check(report(0.85), report(0.90));
        assertFalse(res.passed());
        assertTrue(res.failures().get(0).contains("회귀"), res.failures().toString());
    }

    @Test
    @DisplayName("허용오차 내 변동은 통과")
    void 허용오차_내_통과() {
        var res = new RegressionGate(0.02, List.of()).check(report(0.89), report(0.90));
        assertTrue(res.passed(), res.failures().toString());
    }

    @Test
    @DisplayName("절대 임계(Recall@5≥0.80) 미달 = 실패 (baseline 없어도)")
    void 절대_임계_실패() {
        var gate = new RegressionGate(0.02, List.of(new Threshold("recall@5", 0.80)));
        var res = gate.check(report(0.75), null);
        assertFalse(res.passed());
        assertTrue(res.failures().get(0).contains("임계 미달"), res.failures().toString());
    }
}
