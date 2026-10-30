package com.lia.core.application.history;

/** 사용자당 이력 한도 초과(D73 ⑥) — web 이 409 {@code history_limit} 로 매핑. 메시지는 화면에 그대로. */
public class HistoryLimitException extends RuntimeException {

    public HistoryLimitException(int maxPerUser) {
        super("이력은 최대 " + maxPerUser + "개까지 저장할 수 있어요.");
    }
}
