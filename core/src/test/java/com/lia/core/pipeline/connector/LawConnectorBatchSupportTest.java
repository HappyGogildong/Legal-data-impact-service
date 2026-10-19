package com.lia.core.pipeline.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.connector.PendingLawFeed.PendingList;

/** 배치가 기대는 커넥터 판단 로직 — 목록 완주 판정·Retry-After 해석(순수 로직, API 불필요). */
class LawConnectorBatchSupportTest {

    private static RawLaw head(String lawId) {
        return new RawLaw(lawId, "M-" + lawId, null, "시행예정", LocalDate.of(2027, 1, 1), null, null, null);
    }

    @Test
    @DisplayName("목록 완주 — 받은 건수가 총건수 이상이면 완주, 모자라면 미완주")
    void 완주판정() {
        assertTrue(PendingList.of(List.of(head("1"), head("2")), 2).complete());
        assertFalse(PendingList.of(List.of(head("1")), 2).complete(), "중간에 끊긴 목록");
        assertTrue(PendingList.of(List.of(), 0).complete(), "시행예정이 0건");
    }

    @Test
    @DisplayName("총건수를 모르면(음수) 완주를 확인할 수 없으므로 미완주 — 사라짐 판정을 막는다")
    void 총건수모름_미완주() {
        assertFalse(PendingList.of(List.of(head("1")), -1).complete());
    }

    @Test
    @DisplayName("Retry-After — 초 단위 값을 따르되 기본 백오프보다 짧게는 안 기다리고 30초 상한")
    void retryAfter() {
        assertEquals(3_000, LawConnector.retryAfterMillis("3", 500));
        assertEquals(1_000, LawConnector.retryAfterMillis("0", 1_000), "기본 백오프보다 짧게는 안 기다림");
        assertEquals(30_000, LawConnector.retryAfterMillis("120", 500), "상한");
        assertEquals(500, LawConnector.retryAfterMillis(null, 500), "헤더 없음");
        assertEquals(500, LawConnector.retryAfterMillis("Wed, 21 Oct 2026 07:28:00 GMT", 500), "날짜 형식은 미지원 → 기본");
    }
}
