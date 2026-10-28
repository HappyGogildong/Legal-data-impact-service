package com.lia.core.store;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.pipeline.dispatch.PersonalizationQuota;

/**
 * 하루 개인화 분석 한도(D69) — {@code personalization_usage_daily}. Opus 호출 직전에 1회 차감한다.
 * 한 문장 upsert 의 {@code WHERE count < 한도} 가 동시 요청에도 한도를 넘지 않게 한다(넘으면 갱신 0행 → false).
 * 날짜는 서울 기준({@link LawViewStore}와 같은 식).
 */
public class PersonalizationUsageStore implements PersonalizationQuota {

    private static final String TODAY_SEOUL = "CAST(now() AT TIME ZONE 'Asia/Seoul' AS date)";

    private final JdbcClient jdbc;
    private final int dailyLimit;

    public PersonalizationUsageStore(JdbcClient jdbc, int dailyLimit) {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("하루 개인화 한도(lia.personalization.daily-limit)는 1 이상이어야 합니다: " + dailyLimit);
        }
        this.jdbc = jdbc;
        this.dailyLimit = dailyLimit;
    }

    @Override
    public boolean tryConsume(UUID userId) {
        int changed = jdbc.sql("""
                INSERT INTO personalization_usage_daily (user_id, usage_date, count)
                VALUES (:userId, %s, 1)
                ON CONFLICT (user_id, usage_date) DO UPDATE SET count = personalization_usage_daily.count + 1
                WHERE personalization_usage_daily.count < :limit
                """.formatted(TODAY_SEOUL))
                .param("userId", userId)
                .param("limit", dailyLimit)
                .update();
        return changed > 0;
    }
}
