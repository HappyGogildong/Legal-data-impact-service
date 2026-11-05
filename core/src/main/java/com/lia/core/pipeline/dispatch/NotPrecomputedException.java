package com.lia.core.pipeline.dispatch;

/** 요청한 Layer A 차원의 유효한 저장본이 없다(D80) — 온라인은 Opus 를 부르지 않고 그 차원을 미충족으로 둔다. */
public class NotPrecomputedException extends RuntimeException {

    public NotPrecomputedException(String message) {
        super(message);
    }
}
