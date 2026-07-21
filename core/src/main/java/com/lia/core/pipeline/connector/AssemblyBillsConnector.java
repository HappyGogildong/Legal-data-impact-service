package com.lia.core.pipeline.connector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.lia.core.config.LiaSourceProperties;

/**
 * 열린국회정보 OpenAPI 커넥터 — 의원발의 법률안 (신뢰 출처 수집).
 *
 * 실 API 검증 지식(Python 참조 구현에서 승계):
 *  - AGE(국회 대수)는 필수 파라미터 — 빠지면 ERROR-300
 *  - 오류 응답은 최상위 {"RESULT": {...}} 형태 (AssemblyEnvelope 참고)
 *  - 필드: BILL_ID / BILL_NO / BILL_NAME / COMMITTEE / PROPOSE_DT / PROC_RESULT ...
 */
public class AssemblyBillsConnector implements SourceConnector {

    private final RestClient http;
    private final LiaSourceProperties.Assembly props;

    public AssemblyBillsConnector(RestClient.Builder builder, LiaSourceProperties.Assembly props) {
        this.props = props;
        this.http = builder.baseUrl(props.base()).build();
    }

    @Override
    public String sourceType() {
        return "assembly";
    }

    @Override
    public List<RawBill> search(String query, int limit) {
        requireKey();
        List<RawBill> collected = new ArrayList<>();
        int pindex = 1;
        while (collected.size() < limit) {
            int psize = Math.min(props.pageSize(), limit - collected.size());
            Map<String, Object> payload = request(Map.of("BILL_NAME", query), pindex, psize);
            List<Map<String, Object>> rows = AssemblyEnvelope.extractRows(payload);
            if (rows.isEmpty()) break;
            rows.forEach(r -> collected.add(toRaw(r)));
            if (rows.size() < psize) break;   // 마지막 페이지
            pindex++;
        }
        return collected.size() > limit ? collected.subList(0, limit) : collected;
    }

    @Override
    public RawBill getByBillNo(String billNo) {
        requireKey();
        Map<String, Object> payload = request(Map.of("BILL_NO", billNo), 1, 10);
        for (Map<String, Object> row : AssemblyEnvelope.extractRows(payload)) {
            if (String.valueOf(row.get("BILL_NO")).equals(String.valueOf(billNo))) {
                return toRaw(row);
            }
        }
        return null;
    }

    @Override
    public RawBill fetch(String sourceId) {
        requireKey();
        Map<String, Object> payload = request(Map.of("BILL_ID", sourceId), 1, 5);
        List<Map<String, Object>> rows = AssemblyEnvelope.extractRows(payload);
        if (rows.isEmpty()) throw new AssemblyApiException("BILL_ID=" + sourceId + " 조회 결과 없음");
        return toRaw(rows.get(0));
    }

    // --- 내부 ------------------------------------------------------------

    private void requireKey() {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException("ASSEMBLY_API_KEY 미설정 — .env/application.yml 확인");
        }
    }

    private Map<String, Object> request(Map<String, String> extra, int pindex, int psize) {
        var uri = UriComponentsBuilder.fromPath("/" + props.service())
                .queryParam("KEY", props.apiKey())
                .queryParam("Type", "json")
                .queryParam("AGE", props.age())      // 필수 파라미터
                .queryParam("pIndex", pindex)
                .queryParam("pSize", psize);
        extra.forEach(uri::queryParam);

        RuntimeException last = null;
        for (int attempt = 0; attempt < props.maxRetries(); attempt++) {
            try {
                Map<String, Object> payload = http.get()
                        .uri(uri.build().toUriString())
                        .retrieve()
                        .body(new ParameterizedTypeReference<LinkedHashMap<String, Object>>() {});
                AssemblyEnvelope.checkResult(payload);   // ERROR-* 면 예외
                return payload;
            } catch (AssemblyApiException e) {
                throw e;                                 // API 논리 오류는 재시도 무의미
            } catch (RuntimeException e) {               // 5xx/네트워크 → 지수 백오프
                last = e;
                try {
                    Thread.sleep((long) (500 * Math.pow(2, attempt)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private RawBill toRaw(Map<String, Object> row) {
        Object id = row.get("BILL_ID") != null ? row.get("BILL_ID") : row.get("BILL_NO");
        Object title = row.get("BILL_NAME") != null ? row.get("BILL_NAME") : row.get("TITLE");
        return new RawBill(
                sourceType(),
                id == null ? "" : String.valueOf(id),
                row.get("BILL_NO") == null ? null : String.valueOf(row.get("BILL_NO")),
                title == null ? "" : String.valueOf(title),
                row);
    }
}
