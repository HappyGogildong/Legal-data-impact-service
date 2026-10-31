package com.lia.core.application.subscription;

/** 사용자당 구독 한도 초과(D74 ⑥) — web 이 409 {@code subscription_limit} 로 매핑. 메시지는 화면에 그대로. */
public class SubscriptionLimitException extends RuntimeException {

    public SubscriptionLimitException(int maxPerUser) {
        super("구독은 최대 " + maxPerUser + "개까지 할 수 있어요.");
    }
}
