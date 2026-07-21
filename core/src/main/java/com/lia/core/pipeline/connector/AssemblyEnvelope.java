package com.lia.core.pipeline.connector;

import java.util.List;
import java.util.Map;

/**
 * 열린국회정보 OpenAPI 응답 봉투 파싱 (순수 함수 — 단위 테스트 대상).
 *
 * 정상 응답:  { "<SERVICE>": [ {"head":[{...},{"RESULT":{CODE,MESSAGE}}]}, {"row":[...]} ] }
 * 오류 응답:  { "RESULT": {CODE:"ERROR-xxx", MESSAGE} }   ← 최상위로 온다(주의)
 * INFO-200(데이터 없음)은 정상 취급.
 *
 * Python 참조 구현: pipeline/.../connectors/assembly_bills.py (_extract_rows/_check_result)
 */
public final class AssemblyEnvelope {

    private AssemblyEnvelope() {}

    public record Result(String code, String message) {}

    /** head 또는 최상위의 RESULT 코드 추출. 없으면 null. */
    @SuppressWarnings("unchecked")
    public static Result resultCode(Map<String, Object> payload) {
        // 오류 응답: 최상위 RESULT
        Object top = payload.get("RESULT");
        if (top instanceof Map<?, ?> r) {
            return new Result((String) r.get("CODE"), (String) r.get("MESSAGE"));
        }
        // 정상 응답: 서비스 봉투 안 head[].RESULT
        for (Object value : payload.values()) {
            if (value instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> m && m.containsKey("head")) {
                        for (Object h : (List<Object>) m.get("head")) {
                            if (h instanceof Map<?, ?> hm && hm.containsKey("RESULT")) {
                                Map<String, Object> r = (Map<String, Object>) hm.get("RESULT");
                                return new Result((String) r.get("CODE"), (String) r.get("MESSAGE"));
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** ERROR-* 면 예외. INFO-200(데이터 없음)은 통과. */
    public static void checkResult(Map<String, Object> payload) {
        Result r = resultCode(payload);
        if (r != null && r.code() != null && r.code().startsWith("ERROR")) {
            throw new AssemblyApiException(r.code() + ": " + r.message());
        }
    }

    /** [head, row] 중첩 구조에서 row 목록만 추출(없으면 빈 리스트). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractRows(Map<String, Object> payload) {
        for (Object value : payload.values()) {
            if (value instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> m && m.containsKey("row")) {
                        return (List<Map<String, Object>>) m.get("row");
                    }
                }
            }
        }
        return List.of();
    }
}
