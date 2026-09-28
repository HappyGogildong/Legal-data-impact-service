package com.lia.core.application.account;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.auth.Account;
import com.lia.core.testsupport.InMemoryAccountStore;

/** AccountUseCase — 알림 이메일 동의는 이메일이 있어야 하고, 철회·계정 삭제는 멱등. */
class AccountUseCaseTest {

    final InMemoryAccountStore store = new InMemoryAccountStore();
    final AccountUseCase useCase = new AccountUseCase(store);
    final UUID userId = store.findOrCreate("google", "g-1").userId();

    @Test
    @DisplayName("알림 동의 → email·동의 일시 저장")
    void 알림동의() {
        Account account = useCase.agreeNotificationEmail(userId, "user@gmail.com");

        assertEquals("user@gmail.com", account.email());
        assertNotNull(account.emailConsentedAt());
    }

    @Test
    @DisplayName("IdP 이메일 없음(null·공백) → IllegalArgumentException, 저장 안 됨")
    void 알림동의_이메일없음() {
        assertThrows(IllegalArgumentException.class, () -> useCase.agreeNotificationEmail(userId, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.agreeNotificationEmail(userId, " "));
        assertNull(useCase.find(userId).orElseThrow().email());
    }

    @Test
    @DisplayName("알림 철회 → email·동의 일시 null, 두 번 불러도 됨")
    void 알림철회() {
        useCase.agreeNotificationEmail(userId, "user@gmail.com");

        useCase.withdrawNotificationEmail(userId);
        useCase.withdrawNotificationEmail(userId);

        Account account = useCase.find(userId).orElseThrow();
        assertNull(account.email());
        assertNull(account.emailConsentedAt());
    }

    @Test
    @DisplayName("계정 삭제 → 조회 empty")
    void 계정삭제() {
        useCase.delete(userId);

        assertTrue(useCase.find(userId).isEmpty());
    }
}
