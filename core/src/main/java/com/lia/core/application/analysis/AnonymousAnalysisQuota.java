package com.lia.core.application.analysis;

/** 익명 분석 요청의 서비스 전체 하루 상한(D81). 구현은 {@code AnonymousAnalysisUsageStore}. */
@FunctionalInterface
public interface AnonymousAnalysisQuota {

    /** 오늘 상한 안이면 1회 차감하고 true, 상한에 닿았으면 false. */
    boolean tryConsume();
}
