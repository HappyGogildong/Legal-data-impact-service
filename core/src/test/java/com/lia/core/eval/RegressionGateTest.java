package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회귀 게이트 — 기준선 대비 나빠진 방향의 변화만 실패시킨다(D83). LLM 판정 없음. */
class RegressionGateTest {

    static final RagConfig CONFIG = new RagConfig(40, 10, 0.3);
    final RegressionGate gate = new RegressionGate(0.02, Set.of("refusal.over"));

    static EvalReport report(Map<String, Measure> metrics) {
        return new EvalReport(CONFIG, 100, 30, metrics);
    }

    @Test
    @DisplayName("높을수록 좋은 지표가 허용 오차보다 떨어지면 실패")
    void 하락_실패() {
        var result = gate.check(report(Map.of("discovery.hit@3", new Measure(0.85, 100))),
                report(Map.of("discovery.hit@3", new Measure(0.90, 100))));

        assertFalse(result.passed());
        assertTrue(result.failures().get(0).contains("discovery.hit@3"), result.failures().toString());
    }

    @Test
    @DisplayName("허용 오차 안의 변동은 통과")
    void 허용_오차_안_통과() {
        var result = gate.check(report(Map.of("discovery.hit@3", new Measure(0.89, 100))),
                report(Map.of("discovery.hit@3", new Measure(0.90, 100))));

        assertTrue(result.passed(), result.failures().toString());
    }

    @Test
    @DisplayName("한 문항 차이는 허용하고 두 문항부터 실패")
    void 한_문항_차이_허용() {
        var baseline = report(Map.of("law.resolution", new Measure(27.0 / 30, 30)));

        assertTrue(gate.check(report(Map.of("law.resolution", new Measure(26.0 / 30, 30))), baseline).passed());
        assertFalse(gate.check(report(Map.of("law.resolution", new Measure(25.0 / 30, 30))), baseline).passed());
    }

    @Test
    @DisplayName("낮을수록 좋은 지표는 오르면 실패, 내리면 통과")
    void 낮을수록_좋은_지표() {
        var baseline = report(Map.of("refusal.over", new Measure(0.0, 100)));

        assertFalse(gate.check(report(Map.of("refusal.over", new Measure(0.10, 100))), baseline).passed());
        assertTrue(gate.check(report(Map.of("refusal.over", new Measure(0.0, 100))),
                report(Map.of("refusal.over", new Measure(0.10, 100)))).passed());
    }

    @Test
    @DisplayName("기준선에 없는 지표는 비교하지 않는다")
    void 기준선에_없는_지표() {
        var result = gate.check(report(Map.of("article.match", new Measure(0.1, 10))), report(Map.of()));

        assertTrue(result.passed(), result.failures().toString());
    }

    @Test
    @DisplayName("기준선에 있던 지표가 사라지면 실패")
    void 사라진_지표는_실패() {
        var result = gate.check(report(Map.of()), report(Map.of("refusal.recall", new Measure(1.0, 5))));

        assertFalse(result.passed());
        assertTrue(result.failures().get(0).contains("refusal.recall"), result.failures().toString());
    }

    @Test
    @DisplayName("보고서는 JSON 으로 내보내고 그대로 다시 읽는다")
    void 보고서_JSON_왕복() {
        var original = report(Map.of("discovery.mrr", new Measure(0.375, 2), "refusal.recall", new Measure(1.0, 3)));

        assertEquals(original, EvalReport.fromJson(original.toJson()));
        assertFalse(original.toJson().contains("\n"), "한 줄 JSON — 로그에서 잘라 쓰기 쉽게");
    }
}
