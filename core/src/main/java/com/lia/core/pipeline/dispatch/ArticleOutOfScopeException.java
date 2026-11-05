package com.lia.core.pipeline.dispatch;

/** 변경점 질문이 지목한 조문이 이번 개정의 변경 범위 밖이다(D82) — 그 차원을 사유와 함께 미충족으로 둔다. */
public class ArticleOutOfScopeException extends RuntimeException {

    private final Unmet.Reason reason;

    public ArticleOutOfScopeException(Unmet.Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Unmet.Reason reason() {
        return reason;
    }
}
