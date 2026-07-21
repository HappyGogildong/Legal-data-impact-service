package com.lia.core.pipeline.resolve;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.connector.RawBill;
import com.lia.core.pipeline.connector.SourceConnector;

/**
 * 해소 4상태 단위 테스트 — Python tests/test_pipeline.py 케이스 승계.
 */
class SourceAnalyzerTest {

    /** 오프라인 페이크 커넥터 (동명 법안 포함 — 현실에서 흔함) */
    static class FakeConn implements SourceConnector {
        static final List<String[]> DATA = List.of(
                new String[]{"PRC1", "2200001", "주택임대차보호법 일부개정법률안"},
                new String[]{"PRC2", "2200002", "주택임대차보호법 일부개정법률안"},
                new String[]{"PRC3", "2200010", "소득세법 일부개정법률안"});

        @Override public String sourceType() { return "fake"; }

        @Override
        public List<RawBill> search(String query, int limit) {
            return DATA.stream()
                    .filter(d -> query != null && !query.isBlank()
                            && java.util.Arrays.stream(query.split("\\s+")).anyMatch(d[2]::contains))
                    .limit(limit)
                    .map(this::raw)
                    .toList();
        }

        @Override
        public RawBill fetch(String sourceId) {
            return DATA.stream().filter(d -> d[0].equals(sourceId)).map(this::raw)
                    .findFirst().orElseThrow();
        }

        @Override
        public RawBill getByBillNo(String billNo) {
            return DATA.stream().filter(d -> d[1].equals(String.valueOf(billNo))).map(this::raw)
                    .findFirst().orElse(null);
        }

        private RawBill raw(String[] d) {
            return new RawBill(sourceType(), d[0], d[1], d[2], Map.of());
        }
    }

    private final SourceAnalyzer sa = new SourceAnalyzer(List.of(new FakeConn()));

    @Test
    void 의안번호_정확조회는_RESOLVED() {
        var r = sa.resolve("의안번호 2200001");
        assertEquals(ResolutionState.RESOLVED, r.state());
        assertEquals("2200001", r.resolved().billNo());
    }

    @Test
    void 미등록_의안번호는_NOT_FOUND_YET() {
        assertEquals(ResolutionState.NOT_FOUND_YET, sa.resolve("의안번호 9999999").state());
    }

    @Test
    void 정확한_법안명은_RESOLVED() {
        var r = sa.resolve("소득세법 일부개정법률안");
        assertEquals(ResolutionState.RESOLVED, r.state());
    }

    @Test
    void 동명_다수는_AMBIGUOUS() {
        var r = sa.resolve("주택임대차보호법 일부개정법률안");
        assertEquals(ResolutionState.AMBIGUOUS, r.state());
        assertEquals(2, r.candidates().size());
    }

    @Test
    void 미등록_법안표현은_NOT_FOUND_YET() {
        assertEquals(ResolutionState.NOT_FOUND_YET,
                sa.resolve("아직 발의 안 된 가상의 무슨무슨 법률안").state());
    }

    @Test
    void 법안_아닌_입력은_UNVERIFIED() {
        assertEquals(ResolutionState.UNVERIFIED, sa.resolve("오늘 점심 뭐 먹지").state());
    }

    @Test
    void 커넥터_없어도_fail_closed_지어내지_않음() {
        var empty = new SourceAnalyzer(List.of());
        assertEquals(ResolutionState.NOT_FOUND_YET, empty.resolve("무슨무슨 법률안").state());
        assertEquals(ResolutionState.UNVERIFIED, empty.resolve("점심 메뉴 추천").state());
    }
}
