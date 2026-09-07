package com.lia.core.pipeline.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.QueryType;

/** DimensionHandlerRegistry 단위 — 타입 매핑 + 중복 타입 구성오류 검출. */
class DimensionHandlerRegistryTest {

    static DimensionHandler h(QueryType t) {
        return new DimensionHandler() {
            public QueryType type() { return t; }
            public AnalyzeResponse handle(DispatchContext ctx) { return null; }
        };
    }

    @Test
    void 타입으로_핸들러를_찾고_없으면_empty() {
        DimensionHandlerRegistry reg = new DimensionHandlerRegistry(
                List.of(h(QueryType.SUMMARY), h(QueryType.DIFF)));

        assertTrue(reg.get(QueryType.SUMMARY).isPresent());
        assertTrue(reg.get(QueryType.DIFF).isPresent());
        assertTrue(reg.get(QueryType.LOOKUP).isEmpty());
    }

    @Test
    void 중복_타입은_구성오류로_실패() {
        assertThrows(IllegalStateException.class,
                () -> new DimensionHandlerRegistry(List.of(h(QueryType.SUMMARY), h(QueryType.SUMMARY))));
    }
}
