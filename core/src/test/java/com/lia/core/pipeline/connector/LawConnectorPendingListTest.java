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
import com.lia.core.pipeline.connector.PendingLawFeed.PendingList;

/**
 * 시행예정 목록·검색 요청 — 항상 {@code nw=2}(시행예정만)를 붙인다(D77). {@code nw} 를 빼면
 * API 가 정상 봉투로 0건을 준다(2026-11-01 실측). 실 API 대신 로컬 HTTP 서버가 응답을 흉내 낸다(쿼터 0).
 */
class LawConnectorPendingListTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 2);
    private static final LocalDate TO = LocalDate.of(2027, 11, 2);

    private HttpServer server;
    private final List<Map<String, String>> requests = new ArrayList<>();
    private LawConnector connector;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/DRF/lawSearch.do", this::respond);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        connector = new LawConnector(RestClient.builder(), new LiaSourceProperties.Law("test-oc", base, 5, 1));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** 실 API 처럼 {@code nw=2} 가 없으면 0건, 있으면 시행예정 1건을 준다. */
    private void respond(HttpExchange ex) throws IOException {
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : ex.getRequestURI().getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        requests.add(query);
        String json = "2".equals(query.get("nw"))
                ? """
                  {"LawSearch":{"totalCnt":"1","law":[{"법령ID":"001809","법령일련번호":"283191","법령명한글":"주택법",
                   "현행연혁코드":"시행예정","시행일자":"20270309","공포일자":"20260908","공포번호":"21902"}]}}"""
                : """
                  {"LawSearch":{"totalCnt":"0"}}""";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    @DisplayName("listPending — eflaw 목록 요청에 nw=2 를 붙여 시행예정을 받는다")
    void 목록_nw2() {
        List<RawLaw> pending = connector.listPending(FROM, TO, 5);

        assertEquals(1, pending.size());
        assertEquals("001809", pending.get(0).lawId());
        Map<String, String> request = requests.get(0);
        assertEquals("eflaw", request.get("target"));
        assertEquals("2", request.get("nw"), "시행예정만");
        assertEquals("20261102~20271102", request.get("efYd"));
    }

    @Test
    @DisplayName("listAllPending — 배치 전량 목록도 nw=2. 0건이 완주로 판정돼 사라짐(철회)으로 번지지 않게")
    void 전량목록_nw2() {
        PendingList list = connector.listAllPending(FROM, TO);

        assertEquals(1, list.heads().size());
        assertTrue(list.complete());
        assertTrue(requests.stream().allMatch(r -> "2".equals(r.get("nw"))), "모든 페이지 요청에 nw=2");
    }

    @Test
    @DisplayName("searchPending — 법령명 검색 요청에도 nw=2")
    void 검색_nw2() {
        List<RawLaw> found = connector.searchPending("주택법", FROM, TO, 5);

        assertEquals(1, found.size());
        Map<String, String> request = requests.get(0);
        assertEquals("주택법", request.get("query"));
        assertEquals("2", request.get("nw"), "시행예정만");
    }
}
