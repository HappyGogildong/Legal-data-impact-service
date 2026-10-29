package com.lia.core.pipeline.discover;

import java.time.LocalDate;

/**
 * 후보 카드 한 장(S04) — 원문 대신 쉬운 요약 첫 문장을 보여 주고, 원문은 근거 칩으로만 잇는다(D70).
 *
 * @param summary      쉬운 요약 첫 문장 · 요약이 없으면 개정 이유 앞부분 · 둘 다 없으면 null
 * @param summaryReady 쉬운 요약 저장본에서 왔는가(false 면 "요약 준비 중")
 * @param matched      검색에 걸린 근거 — 조문 또는 개정 이유
 */
public record DiscoveredLaw(String lawId, String title, LocalDate effectiveDate, String ministry,
                            String summary, boolean summaryReady, MatchedSource matched) {

    /** @param sourceId 인용키(분할 접미사 없음) · @param label "제46조 입주자의 거주의무" · "개정 이유" */
    public record MatchedSource(String sourceId, String label) {}
}
