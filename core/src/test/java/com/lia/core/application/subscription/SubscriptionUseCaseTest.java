package com.lia.core.application.subscription;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.application.bookmark.UnknownLawException;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryLawSubscriptionStore;
import com.lia.core.testsupport.InMemoryLawSubscriptionStore.KnownLaw;

/** 구독 유스케이스 — 시점 검증·결과→예외·멱등. */
class SubscriptionUseCaseTest {

    InMemoryAccountStore accounts;
    SubscriptionUseCase subscriptions;
    UUID userId;

    @BeforeEach
    void setup() {
        accounts = new InMemoryAccountStore();
        subscriptions = new SubscriptionUseCase(new InMemoryLawSubscriptionStore(accounts, 1, Map.of(
                "A", new KnownLaw("가법", LocalDate.of(2026, 11, 9)),
                "B", new KnownLaw("나법", null))));
        userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
    }

    @Test
    void 허용_시점만_받는다() {
        assertThrows(IllegalArgumentException.class, () -> subscriptions.subscribe(userId, "A", 3));
        assertThrows(IllegalArgumentException.class, () -> subscriptions.subscribe(userId, "A", null));
        subscriptions.subscribe(userId, "A", 30);
        assertEquals(30, subscriptions.list(userId).getFirst().notifyBefore());
    }

    @Test
    void 결과를_예외로() {
        assertThrows(UnknownLawException.class, () -> subscriptions.subscribe(userId, "Z", 7));
        subscriptions.subscribe(userId, "A", 7);
        SubscriptionLimitException e = assertThrows(SubscriptionLimitException.class,
                () -> subscriptions.subscribe(userId, "B", 7));
        assertEquals("구독은 최대 1개까지 할 수 있어요.", e.getMessage());
        assertThrows(AccountNotFoundException.class, () -> subscriptions.subscribe(UUID.randomUUID(), "A", 7));
    }

    @Test
    void 시점_변경과_해지는_멱등() {
        subscriptions.subscribe(userId, "A", 7);
        subscriptions.subscribe(userId, "A", 1);
        subscriptions.unsubscribe(userId, "A");
        subscriptions.unsubscribe(userId, "A");
        assertTrue(subscriptions.list(userId).isEmpty());
    }
}
