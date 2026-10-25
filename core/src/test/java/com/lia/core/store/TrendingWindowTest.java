package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 집계 구간 — 이름 파싱과 일 수. */
class TrendingWindowTest {

    @Test
    @DisplayName("day·week·month → 1·7·30일, 그 밖은 IllegalArgumentException")
    void 파싱() {
        assertEquals(1, TrendingWindow.parse("day").days());
        assertEquals(7, TrendingWindow.parse("week").days());
        assertEquals(30, TrendingWindow.parse("month").days());
        for (String bad : new String[] {"year", "WEEK", "", " "}) {
            assertThrows(IllegalArgumentException.class, () -> TrendingWindow.parse(bad), bad);
        }
    }
}
