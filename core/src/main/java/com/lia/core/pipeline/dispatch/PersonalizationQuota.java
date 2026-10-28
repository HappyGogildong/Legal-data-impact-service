package com.lia.core.pipeline.dispatch;

import java.util.UUID;

/** 사용자별 하루 개인화 분석 한도(D69) — Opus 호출 직전에 1회 차감. 한도 안이면 true(차감됨), 넘었으면 false. */
public interface PersonalizationQuota {
    boolean tryConsume(UUID userId);
}
