package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회귀 게이트 규칙 일치(D83) — {@code tools/eval/render_report.py} 와 같은 픽스처({@code eval/gate-parity.json})로
 * 실패 지표가 같은지 본다. HTML 보고서의 판정과 실행기 판정이 어긋나면 여기 또는 Python 시험이 깨진다.
 */
class RegressionGateParityTest {

    record Case(String name, double tolerance, Map<String, Measure> baseline, Map<String, Measure> report,
                List<String> expectedFailures) {}

    record Fixture(List<String> lowerIsBetter, List<Case> cases) {}

    static final RagConfig CONFIG = new RagConfig(40, 10, 0.3);
    static final ObjectMapper JSON = JsonMapper.builder().build();

    static Fixture fixture() throws IOException {
        try (InputStream in = RegressionGateParityTest.class.getResourceAsStream("/eval/gate-parity.json")) {
            assertNotNull(in, "eval/gate-parity.json 이 테스트 클래스패스에 없다");
            return JSON.readValue(in, Fixture.class);
        }
    }

    static EvalReport report(Map<String, Measure> metrics) {
        return new EvalReport(CONFIG, 100, 30, 0, metrics);
    }

    /** {@code "회귀 <키>: …"}·{@code "지표 없음 <키>: …"} 에서 지표 키를 꺼낸다. */
    static Set<String> failedKeys(List<String> failures) {
        Set<String> keys = new HashSet<>();
        for (String failure : failures) {
            String rest = failure.startsWith("회귀 ") ? failure.substring("회귀 ".length())
                    : failure.startsWith("지표 없음 ") ? failure.substring("지표 없음 ".length())
                    : null;
            assertNotNull(rest, "모르는 실패 메시지 형식: " + failure);
            keys.add(rest.substring(0, rest.indexOf(':')));
        }
        return keys;
    }

    @Test
    @DisplayName("낮을수록 좋은 지표 목록이 픽스처(=Python 도구)와 같다")
    void 낮을수록_좋은_지표_일치() throws IOException {
        assertEquals(Set.copyOf(fixture().lowerIsBetter()), OfflineMetrics.LOWER_IS_BETTER);
    }

    @TestFactory
    @DisplayName("사례마다 실패 지표가 픽스처와 같다")
    Stream<DynamicTest> 사례별_실패_지표() throws IOException {
        return fixture().cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            var gate = new RegressionGate(c.tolerance(), OfflineMetrics.LOWER_IS_BETTER);
            var result = gate.check(report(c.report()), report(c.baseline()));

            assertEquals(Set.copyOf(c.expectedFailures()), failedKeys(result.failures()), result.failures().toString());
            assertEquals(c.expectedFailures().isEmpty(), result.passed());
        }));
    }
}
