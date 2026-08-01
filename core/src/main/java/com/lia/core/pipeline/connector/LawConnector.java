package com.lia.core.pipeline.connector;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.config.LiaSourceProperties;

/**
 * 국가법령정보(law.go.kr DRF) 커넥터 — <b>MVP의 유일한 수집 경로</b>(D42).
 *
 * <p>target 두 개를 한 커넥터가 담당한다. 응답 스키마가 동일하기 때문이다.
 * <ul>
 *   <li>{@code eflaw} — 공포 후 시행 대기 법령 = <b>분석 대상</b></li>
 *   <li>{@code law}   — 시행중 법령 = <b>diff 기준선</b></li>
 * </ul>
 *
 * <p>{@link SourceConnector}(RawBill 계약)를 구현하지 않는다 — 다루는 것이 의안이 아니라
 * 법령이고 {@code billNo}·발의자·심사단계가 존재하지 않는다. 의안 커넥터는 post-MVP다.
 *
 * <p>설계: docs/components/SourceConnector.md §MVP 본문 경로 · 아키텍처 v0.8 §4.1
 */
public class LawConnector {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("uuuuMMdd");

    /** DRF 목록 조회 상한(실측 확인). */
    static final int MAX_PAGE_SIZE = 200;

    private final RestClient http;
    private final LiaSourceProperties.Law props;

    public LawConnector(RestClient.Builder builder, LiaSourceProperties.Law props) {
        this.props = props;
        this.http = builder.baseUrl(props.base()).build();
    }

    public String sourceType() {
        return "law";
    }

    // --- 공개 API --------------------------------------------------------

    /**
     * 시행 대기 법령 목록. {@code efYd} 범위로 "아직 시행되지 않은 것"만 거른다.
     *
     * @param from 시행일 하한(보통 오늘 다음 날)
     * @param to   시행일 상한
     * @param limit 최대 건수. 0 이하면 전량(totalCnt 까지)
     */
    public List<RawLaw> listPending(LocalDate from, LocalDate to, int limit) {
        requireOc();
        List<RawLaw> collected = new ArrayList<>();
        String range = from.format(YMD) + "~" + to.format(YMD);
        int page = 1;
        int total = Integer.MAX_VALUE;

        while (collected.size() < total && (limit <= 0 || collected.size() < limit)) {
            int want = limit <= 0 ? MAX_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, limit - collected.size());
            Map<String, Object> payload = request(UriComponentsBuilder.fromPath("/DRF/lawSearch.do")
                    .queryParam("target", "eflaw")
                    .queryParam("efYd", range)
                    .queryParam("sort", "efasc")
                    .queryParam("page", page)
                    .queryParam("display", want));

            if (total == Integer.MAX_VALUE) {
                int t = LawEnvelope.totalCount(payload);
                total = t < 0 ? Integer.MAX_VALUE : t;
            }
            List<Map<String, Object>> rows = LawEnvelope.extractRows(payload);
            if (rows.isEmpty()) break;
            rows.forEach(r -> collected.add(fromListRow(r)));
            if (rows.size() < want) break;   // 마지막 페이지
            page++;
        }
        return limit > 0 && collected.size() > limit ? collected.subList(0, limit) : collected;
    }

    /** 시행예정 법령 본문. {@code efYd} 는 목록의 시행일자를 그대로 넘긴다. */
    public RawLaw fetchPending(String mst, LocalDate effectiveDate) {
        requireOc();
        var uri = UriComponentsBuilder.fromPath("/DRF/lawService.do")
                .queryParam("target", "eflaw")
                .queryParam("MST", mst);
        if (effectiveDate != null) uri.queryParam("efYd", effectiveDate.format(YMD));
        return fromBody(request(uri), "시행예정", mst);
    }

    /**
     * 시행중(현행) 본문 — diff 기준선.
     *
     * <p><b>반드시 {@code 법령ID} 로 조회한다.</b> {@code MST}(법령일련번호)는 버전마다
     * 달라서 시행중↔시행예정 연결에 쓸 수 없다(실측 확인).
     */
    public RawLaw fetchCurrent(String lawId) {
        requireOc();
        var uri = UriComponentsBuilder.fromPath("/DRF/lawService.do")
                .queryParam("target", "law")
                .queryParam("ID", lawId);
        return fromBody(request(uri), "시행중", null);
    }

    // --- 매핑 ------------------------------------------------------------

    /** 목록 row → RawLaw (본문 없음). */
    private RawLaw fromListRow(Map<String, Object> row) {
        return new RawLaw(
                LawEnvelope.str(row.get("법령ID")),
                LawEnvelope.str(row.get("법령일련번호")),
                LawEnvelope.str(row.get("법령명한글")),
                LawEnvelope.str(row.get("현행연혁코드")),
                LawEnvelope.date(row.get("시행일자")),
                LawEnvelope.date(row.get("공포일자")),
                LawEnvelope.str(row.get("공포번호")),
                row);
    }

    /** 본문 응답 → RawLaw. 헤더는 기본정보에서 뽑고 raw 에 전체 블록을 보존한다. */
    private RawLaw fromBody(Map<String, Object> payload, String statusFallback, String mstFallback) {
        Map<String, Object> root = LawEnvelope.lawRoot(payload);
        Map<String, Object> info = LawEnvelope.basicInfo(root);
        return new RawLaw(
                LawEnvelope.str(info.get("법령ID")),
                mstFallback != null ? mstFallback : LawEnvelope.str(info.get("법령일련번호")),
                LawEnvelope.flatString(info.get("법령명_한글")),
                statusFallback,
                LawEnvelope.date(info.get("시행일자")),
                LawEnvelope.date(info.get("공포일자")),
                LawEnvelope.str(info.get("공포번호")),
                root);
    }

    // --- HTTP ------------------------------------------------------------

    private void requireOc() {
        if (props.oc() == null || props.oc().isBlank()) {
            throw new IllegalStateException("LAW_OC 미설정 — .env/application.yml 확인");
        }
    }

    private Map<String, Object> request(UriComponentsBuilder uri) {
        uri.queryParam("OC", props.oc()).queryParam("type", "JSON");

        RuntimeException last = null;
        for (int attempt = 0; attempt < props.maxRetries(); attempt++) {
            try {
                // 열린국회와 달리 Content-Type 이 application/json 으로 정상이지만,
                // 오류도 HTTP 200 으로 오므로 봉투 검사는 반드시 거친다.
                String raw = http.get().uri(uri.build().toUriString()).retrieve().body(String.class);
                Map<String, Object> payload = parseJson(raw);
                LawEnvelope.checkError(payload);
                return payload;
            } catch (LawApiException e) {
                throw e;                        // API 논리 오류는 재시도 무의미
            } catch (RuntimeException e) {      // 5xx/네트워크 → 지수 백오프
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String raw) {
        if (raw == null || raw.isBlank()) throw new LawApiException("빈 응답");
        return JSON.readValue(raw, LinkedHashMap.class);
    }
}
