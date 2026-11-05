package com.lia.core.store;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.application.analysis.AnonymousAnalysisQuota;

/**
 * 익명 분석의 서비스 전체 하루 상한(D81) — {@code anonymous_analysis_usage_daily}. 분류(Haiku) 전에 1회 차감한다.
 * 한 문장 upsert 의 {@code WHERE count < 상한} 이 동시 요청에도 상한을 넘지 않게 한다({@link PersonalizationUsageStore}와 같은 방식).
 */
public class AnonymousAnalysisUsageStore implements AnonymousAnalysisQuota {

    private final JdbcClient jdbc;
    private final int dailyLimit;

    public AnonymousAnalysisUsageStore(JdbcClient jdbc, int dailyLimit) {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("익명 분석 상한(lia.analysis.anonymous-daily-limit)은 1 이상이어야 합니다.");
        }
        this.jdbc = jdbc;
        this.dailyLimit = dailyLimit;
    }

    @Override
    public boolean tryConsume() {
        int changed = jdbc.sql("""
                INSERT INTO anonymous_analysis_usage_daily (usage_date, count)
                VALUES (CAST(now() AT TIME ZONE 'Asia/Seoul' AS date), 1)
                ON CONFLICT (usage_date) DO UPDATE SET count = anonymous_analysis_usage_daily.count + 1
                WHERE anonymous_analysis_usage_daily.count < :limit
                """)
                .param("limit", dailyLimit)
                .update();
        return changed > 0;
    }
}
