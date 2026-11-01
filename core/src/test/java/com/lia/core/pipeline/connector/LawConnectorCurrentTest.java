package com.lia.core.pipeline.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.lia.core.config.LiaSourceProperties;

/**
 * 현행본(diff 기준선) 조회 — 시행일 기준 현행 행을 찾고 그 MST+시행일로 본문을 받는다(D76).
 * 실 API 대신 로컬 HTTP 서버가 국가법령정보 응답을 흉내 낸다(쿼터 0).
 */
class LawConnectorCurrentTest {

    private HttpServer server;
    private final List<Map<String, String>> requests = new ArrayList<>();
    private String listResponse;
    private String bodyResponse;
    private LawConnector connector;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/DRF/lawSearch.do", ex -> respond(ex, listResponse));
        server.createContext("/DRF/lawService.do", ex -> respond(ex, bodyResponse));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        connector = new LawConnector(RestClient.builder(), new LiaSourceProperties.Law("test-oc", base, 5, 1));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(HttpExchange ex, String json) throws IOException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("path", ex.getRequestURI().getPath());
        for (String pair : ex.getRequestURI().getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        requests.add(query);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String currentRow(String mst, String effectiveDate) {
        return """
                {"법령ID":"001809","법령일련번호":"%s","법령명한글":"주택법","현행연혁코드":"현행",
                 "시행일자":"%s","공포일자":"20260908","공포번호":"21902"}""".formatted(mst, effectiveDate);
    }

    private static String list(int totalCount, String rows) {
        return """
                {"LawSearch":{"totalCnt":"%d","law":[%s]}}""".formatted(totalCount, rows);
    }

    private static String body(String effectiveDate) {
        return """
                {"법령":{"기본정보":{"법령ID":"001809","법령일련번호":"289171","법령명_한글":"주택법",
                 "시행일자":"%s","공포일자":"20260908","공포번호":"21902"}}}""".formatted(effectiveDate);
    }

    @Test
    @DisplayName("현행 행 1건 → 그 행의 MST·시행일로 eflaw 본문을 받는다. target=law 는 부르지 않는다")
    void 현행행으로_본문조회() {
        listResponse = list(1, currentRow("289171", "20260908"));
        bodyResponse = body("20260908");

        RawLaw current = connector.fetchCurrent("001809");

        assertEquals(LocalDate.of(2026, 9, 8), current.effectiveDate(), "시행일 기준 현행(공포본의 마지막 시행일이 아님)");
        assertEquals("289171", current.mst());
        assertEquals("시행중", current.status());

        assertEquals(2, requests.size());
        Map<String, String> listRequest = requests.get(0);
        assertEquals("/DRF/lawSearch.do", listRequest.get("path"));
        assertEquals("eflaw", listRequest.get("target"));
        assertEquals("001809", listRequest.get("LID"));
        assertEquals("3", listRequest.get("nw"), "현행만");
        Map<String, String> bodyRequest = requests.get(1);
        assertEquals("/DRF/lawService.do", bodyRequest.get("path"));
        assertEquals("eflaw", bodyRequest.get("target"));
        assertEquals("289171", bodyRequest.get("MST"));
        assertEquals("20260908", bodyRequest.get("efYd"));
        assertFalse(bodyRequest.containsKey("ID"), "ID 로 조회하지 않는다 — 제정이면 HTML 권한 오류 페이지가 온다");
    }

    @Test
    @DisplayName("현행 행 0건 → null(제정 = 기준선 없음), 본문은 부르지 않는다")
    void 현행행_없음_제정() {
        listResponse = """
                {"LawSearch":{"totalCnt":"0"}}""";

        assertNull(connector.fetchCurrent("015136"));
        assertEquals(1, requests.size(), "목록만");
    }

    @Test
    @DisplayName("현행 행 2건 이상 → 예외(추측해 고르지 않는다)")
    void 현행행_복수() {
        listResponse = list(2, currentRow("289171", "20260908") + "," + currentRow("283191", "20260804"));

        assertThrows(LawApiException.class, () -> connector.fetchCurrent("001809"));
        assertEquals(1, requests.size(), "본문은 부르지 않는다");
    }

    @Test
    @DisplayName("현행 본문의 시행일이 현행 행과 다르면 예외 — 미래본을 기준선으로 쓰지 않는다")
    void 본문시행일_불일치() {
        listResponse = list(1, currentRow("289171", "20260908"));
        bodyResponse = body("20270309");

        assertThrows(LawApiException.class, () -> connector.fetchCurrent("001809"));
    }
}
