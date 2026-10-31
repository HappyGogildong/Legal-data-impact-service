package com.lia.core.application.subscription;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.application.bookmark.UnknownLawException;
import com.lia.core.store.LawSubscriptionStore;
import com.lia.core.store.LawSubscriptionStore.SubscribedLaw;

/**
 * 법령 구독 유스케이스(D74) — 알림 시점 검증, 저장소 결과를 예외로. Spring 애노테이션 없음: {@code config/UserConfig} 의 {@code @Bean}.
 * 설계: docs/components/web/SubscriptionApi.md
 */
public class SubscriptionUseCase {

    /** 허용 알림 시점(일) — 당일·1·7·30일 전(D74 ②). */
    public static final Set<Integer> NOTIFY_BEFORE_DAYS = Set.of(0, 1, 7, 30);

    private final LawSubscriptionStore store;

    public SubscriptionUseCase(LawSubscriptionStore store) {
        this.store = store;
    }

    /**
     * 만들거나 시점 변경(멱등).
     *
     * @throws IllegalArgumentException 허용 밖 시점·누락 — web 이 400
     * @throws UnknownLawException      저장된 버전이 없는 법령 — 404
     * @throws SubscriptionLimitException 한도 초과 — 409
     * @throws AccountNotFoundException 세션의 계정이 없음 — 401
     */
    public void subscribe(UUID userId, String lawId, Integer notifyBefore) {
        if (notifyBefore == null || !NOTIFY_BEFORE_DAYS.contains(notifyBefore)) {
            throw new IllegalArgumentException("notifyBefore 는 0·1·7·30 중 하나여야 합니다.");
        }
        switch (store.subscribe(userId, lawId, notifyBefore)) {
            case SUBSCRIBED, UPDATED -> { }
            case LAW_NOT_FOUND -> throw new UnknownLawException(lawId);
            case LIMIT -> throw new SubscriptionLimitException(store.maxPerUser());
            case ACCOUNT_NOT_FOUND -> throw new AccountNotFoundException();
            default -> throw new IllegalStateException("모르는 결과");
        }
    }

    /** 멱등. */
    public void unsubscribe(UUID userId, String lawId) {
        store.unsubscribe(userId, lawId);
    }

    public List<SubscribedLaw> list(UUID userId) {
        return store.list(userId);
    }
}
