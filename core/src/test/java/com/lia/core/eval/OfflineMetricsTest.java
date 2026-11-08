package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.eval.EvalCase.Kind;
import com.lia.core.pipeline.plan.QueryType;

/** 결과 묶음 → 지표·분모(D83). 손으로 계산한 값과 대조한다. */
class OfflineMetricsTest {

    static EvalCase named(String lawId, String articleNo, QueryType dimension) {
        return new EvalCase("q-" + lawId, Kind.ANSWERABLE, true, List.of(lawId), List.of(), articleNo, dimension);
    }

    static EvalCase topic(String lawId, String... acceptable) {
        return new EvalCase("q-" + lawId, Kind.ANSWERABLE, false, List.of(lawId), List.of(acceptable), null, null);
    }

    static EvalCase of(Kind kind) {
        return new EvalCase("q-" + kind, kind, false, List.of(), List.of(), null, null);
    }

    static CaseOutcome resolved(String lawId, String articleNo, QueryType primary) {
        return new CaseOutcome(false, List.of(lawId), true, articleNo, primary);
    }

    static CaseOutcome found(String... lawIds) {
        return new CaseOutcome(false, List.of(lawIds), false, null, QueryType.LOOKUP);
    }

    static CaseOutcome refused() {
        return new CaseOutcome(true, List.of(), false, null, null);
    }

    static void assertMeasure(Map<String, Measure> scores, String key, double value, int n) {
        Measure m = scores.get(key);
        assertNotNull(m, key + " 없음: " + scores.keySet());
        assertEquals(value, m.value(), 1e-9, key);
        assertEquals(n, m.n(), key + " 분모");
    }

    @Test
    @DisplayName("법령 해소 정확도는 이름을 댄 답 있는 문항만 센다")
    void 법령_해소_정확도() {
        var scores = OfflineMetrics.score(
                List.of(named("A", null, null), named("B", null, null), topic("C")),
                List.of(resolved("A", null, null), resolved("A", null, null), found("C")));

        assertMeasure(scores, "law.resolution", 0.5, 2);
    }

    @Test
    @DisplayName("해소 후보가 여럿(미해소)이면 해소 실패로 센다")
    void 미해소는_해소_실패() {
        var scores = OfflineMetrics.score(List.of(named("A", null, null)), List.of(found("A", "B")));

        assertMeasure(scores, "law.resolution", 0.0, 1);
    }

    @Test
    @DisplayName("발견 Hit@3·MRR 은 이름 없는 답 있는 문항으로 잰다")
    void 발견_Hit3과_MRR() {
        var scores = OfflineMetrics.score(
                List.of(topic("A"), topic("B"), named("C", null, null)),
                List.of(found("X", "A"), found("X", "Y", "Z", "B"), resolved("C", null, null)));

        assertMeasure(scores, "discovery.hit@3", 0.5, 2);
        assertMeasure(scores, "discovery.mrr", (0.5 + 0.25) / 2, 2);
    }

    @Test
    @DisplayName("무관 후보율 = 정답도 허용 법령도 아닌 후보 / 낸 후보 전체")
    void 무관_후보율() {
        var scores = OfflineMetrics.score(
                List.of(topic("A", "C"), topic("B")),
                List.of(found("A", "C", "X"), found()));

        assertMeasure(scores, "discovery.irrelevant", 1.0 / 3, 3);
    }

    @Test
    @DisplayName("조문 일치율은 정답에 조문이 있는 문항만 센다")
    void 조문_일치율() {
        var scores = OfflineMetrics.score(
                List.of(named("A", "18", null), named("A", "105의2", null), named("A", null, null)),
                List.of(resolved("A", "18", null), resolved("A", null, null), resolved("A", "3", null)));

        assertMeasure(scores, "article.match", 0.5, 2);
    }

    @Test
    @DisplayName("관련 법령이 없는 문항에 후보를 내면 센다")
    void 관련_법령_없음_후보율() {
        var scores = OfflineMetrics.score(
                List.of(of(Kind.NO_MATCH), of(Kind.NO_MATCH)),
                List.of(found("X"), found()));

        assertMeasure(scores, "nomatch.candidate_rate", 0.5, 2);
    }

    @Test
    @DisplayName("거부 재현율은 거부 문항, 과잉 거부율은 답 있는 문항이 분모")
    void 거부_재현율과_과잉_거부율() {
        var scores = OfflineMetrics.score(
                List.of(of(Kind.REFUSE), of(Kind.REFUSE), named("A", null, null), topic("B")),
                List.of(refused(), found("X"), refused(), found("B")));

        assertMeasure(scores, "refusal.recall", 0.5, 2);
        assertMeasure(scores, "refusal.over", 0.5, 2);
    }

    @Test
    @DisplayName("의도 분류는 차원별 정밀도·재현율·F1 과 macro F1")
    void 의도_분류() {
        var scores = OfflineMetrics.score(
                List.of(named("A", null, QueryType.DIFF), named("A", null, QueryType.DIFF),
                        named("A", null, QueryType.SUMMARY), named("A", null, QueryType.IMPACT)),
                List.of(resolved("A", null, QueryType.DIFF), resolved("A", null, QueryType.SUMMARY),
                        resolved("A", null, QueryType.SUMMARY), refused()));

        assertMeasure(scores, "intent.DIFF.precision", 1.0, 1);
        assertMeasure(scores, "intent.DIFF.recall", 0.5, 2);
        assertMeasure(scores, "intent.DIFF.f1", 2.0 / 3, 2);
        assertMeasure(scores, "intent.SUMMARY.precision", 0.5, 2);
        assertMeasure(scores, "intent.SUMMARY.recall", 1.0, 1);
        assertMeasure(scores, "intent.IMPACT.recall", 0.0, 1);
        assertMeasure(scores, "intent.IMPACT.f1", 0.0, 1);
        assertFalse(scores.containsKey("intent.IMPACT.precision"), "예측이 없는 차원의 정밀도는 정의되지 않는다");
        assertMeasure(scores, "intent.macro_f1", (2.0 / 3 + 2.0 / 3 + 0.0) / 3, 4);
    }

    @Test
    @DisplayName("분모가 0인 지표는 싣지 않는다")
    void 분모가_0인_지표는_없다() {
        var scores = OfflineMetrics.score(List.of(of(Kind.REFUSE)), List.of(refused()));

        assertEquals(List.of("refusal.recall"), List.copyOf(scores.keySet()));
    }

    @Test
    @DisplayName("문항과 결과 수가 다르면 거부한다")
    void 문항과_결과_수가_다르면_거부() {
        assertThrows(IllegalArgumentException.class,
                () -> OfflineMetrics.score(List.of(of(Kind.REFUSE)), List.of()));
    }
}
