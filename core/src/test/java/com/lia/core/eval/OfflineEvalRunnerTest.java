package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 오프라인 평가 실행 — 보고서 출력과 종료 코드(D83). 가짜 평가 함수만 쓴다(LLM·DB 없음). */
class OfflineEvalRunnerTest {

    static final String GOLD = "eval/sample-gold.json";
    static final String BASELINE = "eval/sample-baseline.json";
    static final RagConfig CONFIG = new RagConfig(40, 10, 0.3);

    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /** 거부 문항("오늘 점심 뭐 먹지")만 거부하는 평가 함수. */
    static CaseOutcome refusesOffTopic(String query) {
        return query.contains("점심")
                ? new CaseOutcome(true, List.of(), false, null, null)
                : new CaseOutcome(false, List.of(), false, null, null);
    }

    int run(Function<String, CaseOutcome> evaluate, String gold, String baseline) {
        return run(evaluate, gold, baseline, lawId -> true);
    }

    int run(Function<String, CaseOutcome> evaluate, String gold, String baseline, Predicate<String> pendingLaw) {
        var runner = new OfflineEvalRunner(evaluate, CONFIG, () -> 123L, pendingLaw, gold, baseline, 0.02,
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return runner.execute();
    }

    String output() {
        return buffer.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    @DisplayName("기준선이 없으면 보고서만 내고 통과한다")
    void 기준선_없음() {
        int code = run(OfflineEvalRunnerTest::refusesOffTopic, GOLD, "eval/no-such-baseline.json");

        assertEquals(OfflineEvalRunner.PASSED, code);
        EvalReport report = EvalReport.fromJson(output());
        assertEquals(4, report.cases());
        assertEquals(123L, report.indexedLaws());
        assertEquals(CONFIG, report.config());
        assertEquals(1.0, report.metrics().get("refusal.recall").value(), 1e-9);
    }

    @Test
    @DisplayName("기준선을 지키면 통과한다")
    void 기준선_통과() {
        assertEquals(OfflineEvalRunner.PASSED, run(OfflineEvalRunnerTest::refusesOffTopic, GOLD, BASELINE));
    }

    @Test
    @DisplayName("기준선보다 나빠지면 회귀로 끝나고 보고서는 그대로 낸다")
    void 회귀() {
        // 답할 수 있는 문항 2개까지 모두 거부 — 과잉 거부율 0 → 1 (분모 2라 한 문항 허용을 넘는다)
        int code = run(query -> new CaseOutcome(true, List.of(), false, null, null), GOLD, BASELINE);

        assertEquals(OfflineEvalRunner.REGRESSED, code);
        assertEquals(1.0, EvalReport.fromJson(output()).metrics().get("refusal.over").value(), 1e-9);
    }

    @Test
    @DisplayName("정답 법령이 더 이상 시행예정이 아닌 문항은 평가하지 않고 뺀 수를 싣는다")
    void 시행일이_지난_문항은_뺀다() {
        List<String> asked = new ArrayList<>();
        int code = run(query -> {
            asked.add(query);
            return refusesOffTopic(query);
        }, GOLD, "eval/no-such-baseline.json", lawId -> !lawId.equals("009999"));

        assertEquals(OfflineEvalRunner.PASSED, code);
        EvalReport report = EvalReport.fromJson(output());
        assertEquals(3, report.cases());
        assertEquals(1, report.excludedCases());
        assertFalse(asked.contains("전세 보증금 돌려받는 제도가 어떻게 바뀌어?"), "뺀 문항은 유료 평가를 하지 않는다");
    }

    @Test
    @DisplayName("정답 세트가 없으면 실행 실패")
    void 정답_세트_없음() {
        assertEquals(OfflineEvalRunner.FAILED,
                run(OfflineEvalRunnerTest::refusesOffTopic, "eval/no-such-gold.json", BASELINE));
    }

    @Test
    @DisplayName("문항 하나라도 평가 중 예외가 나면 실행 실패 — 조용히 빼면 분모가 바뀐다")
    void 문항_예외() {
        int code = run(query -> {
            throw new IllegalStateException("번역기 오류");
        }, GOLD, BASELINE);

        assertEquals(OfflineEvalRunner.FAILED, code);
        assertEquals("", output(), "실패한 실행은 보고서를 내지 않는다");
    }
}
