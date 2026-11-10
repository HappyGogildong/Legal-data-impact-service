package com.lia.core.application.analysis;

/**
 * 질의 이벤트 기록 포트(D83). 구현은 응답을 막거나 실패시키지 않는다 — 스펙:
 * docs/components/observability/QueryEventRecorder.md.
 */
@FunctionalInterface
public interface QueryEventRecorder {

    /** 아무것도 남기지 않는다(기록을 다루지 않는 단위 테스트용). */
    QueryEventRecorder NONE = event -> {};

    void record(QueryEvent event);
}
