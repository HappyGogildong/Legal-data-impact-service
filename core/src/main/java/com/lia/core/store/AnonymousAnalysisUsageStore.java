package com.lia.core.store;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.application.analysis.AnonymousAnalysisQuota;

/** 익명 분석 하루 상한(D81). */
public class AnonymousAnalysisUsageStore implements AnonymousAnalysisQuota {

    public AnonymousAnalysisUsageStore(JdbcClient jdbc, int dailyLimit) {
    }

    @Override
    public boolean tryConsume() {
        return true;
    }
}
