package com.lia.core.pipeline.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 봉투 파싱 단위 테스트 — Python tests/test_pipeline.py 케이스 승계.
 * 네트워크/Spring 컨텍스트 불필요.
 */
class AssemblyEnvelopeTest {

    private Map<String, Object> payload(List<Map<String, Object>> rows, String code) {
        return Map.of("SVC", List.of(
                Map.of("head", List.of(
                        Map.of("list_total_count", rows.size()),
                        Map.of("RESULT", Map.of("CODE", code, "MESSAGE", "x")))),
                Map.of("row", rows)));
    }

    @Test
    void extractRows_정상봉투에서_row만_추출() {
        List<Map<String, Object>> rows = List.of(Map.of("BILL_NO", "1"), Map.of("BILL_NO", "2"));
        assertEquals(rows, AssemblyEnvelope.extractRows(payload(rows, "INFO-000")));
        assertTrue(AssemblyEnvelope.extractRows(Map.of("SVC", List.of())).isEmpty());
    }

    @Test
    void resultCode_head안의_코드를_읽는다() {
        var r = AssemblyEnvelope.resultCode(payload(List.of(), "INFO-200"));
        assertNotNull(r);
        assertEquals("INFO-200", r.code());
    }

    @Test
    void resultCode_오류는_최상위_RESULT로_온다() {   // 실 API 검증 지식
        Map<String, Object> err = Map.of("RESULT",
                Map.of("CODE", "ERROR-300", "MESSAGE", "필수 값이 누락되어 있습니다."));
        var r = AssemblyEnvelope.resultCode(err);
        assertNotNull(r);
        assertEquals("ERROR-300", r.code());
        assertThrows(AssemblyApiException.class, () -> AssemblyEnvelope.checkResult(err));
    }

    @Test
    void checkResult_INFO는_통과() {
        assertDoesNotThrow(() -> AssemblyEnvelope.checkResult(payload(List.of(), "INFO-000")));
        assertDoesNotThrow(() -> AssemblyEnvelope.checkResult(payload(List.of(), "INFO-200"))); // 데이터 없음=정상
    }
}
