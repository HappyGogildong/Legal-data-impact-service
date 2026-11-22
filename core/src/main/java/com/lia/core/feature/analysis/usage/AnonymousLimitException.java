package com.lia.core.feature.analysis.usage;

/** 오늘 익명 분석 상한에 닿았다 — 429(D81). 메시지는 화면에 그대로. */
public class AnonymousLimitException extends RuntimeException {

    public AnonymousLimitException(String message) {
        super(message);
    }
}
