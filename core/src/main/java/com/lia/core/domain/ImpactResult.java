package com.lia.core.domain;

import java.util.List;

/** 커맨드 후처리 산출물. */
public record ImpactResult(
        String billId,
        String persona,
        String summary,
        List<String> impacts,
        List<String> actions,
        List<Citation> citations,
        double confidence
) {
    /** 모든 주장은 일차 출처(조문)로 역추적된다. */
    public record Citation(String articleNo, String quote, boolean verified) {}
}
