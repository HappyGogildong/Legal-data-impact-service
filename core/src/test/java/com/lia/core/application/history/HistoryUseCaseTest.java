package com.lia.core.application.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.store.AnalysisHistoryStore.NewEntry;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryAnalysisHistoryStore;

/** 이력 유스케이스 — 저장소 결과 → 예외(만료·한도·계정 없음·없는 기록), 멱등. */
class HistoryUseCaseTest {

    static final LocalDate EFFECTIVE = LocalDate.of(2026, 11, 8);

    InMemoryAccountStore accounts;
    HistoryUseCase history;
    UUID userId;

    @BeforeEach
    void setup() {
        accounts = new InMemoryAccountStore();
        history = new HistoryUseCase(new InMemoryAnalysisHistoryStore(accounts, 1, Set.of("001809@" + EFFECTIVE)));
        userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
    }

    private static NewEntry entry(String analysisId, String lawId) {
        return new NewEntry(analysisId, "q", lawId, EFFECTIVE, "주택법", "{}");
    }

    @Test
    void 저장은_멱등이고_두_번째는_created_false() {
        SavedHistory first = history.save(userId, entry("a-1", "001809"));
        SavedHistory again = history.save(userId, entry("a-1", "001809"));
        assertTrue(first.created());
        assertFalse(again.created());
        assertEquals(first.id(), again.id());
    }

    @Test
    void 법령_버전이_없으면_AnalysisExpiredException() {
        assertThrows(AnalysisExpiredException.class, () -> history.save(userId, entry("a-1", "999999")));
    }

    @Test
    void 한도를_넘으면_HistoryLimitException_메시지에_한도() {
        history.save(userId, entry("a-1", "001809"));
        HistoryLimitException e = assertThrows(HistoryLimitException.class, () -> history.save(userId, entry("a-2", "001809")));
        assertEquals("이력은 최대 1개까지 저장할 수 있어요.", e.getMessage());
    }

    @Test
    void 계정이_없으면_AccountNotFoundException() {
        assertThrows(AccountNotFoundException.class, () -> history.save(UUID.randomUUID(), entry("a-1", "001809")));
    }

    @Test
    void 없거나_남의_기록은_HistoryNotFoundException() {
        long id = history.save(userId, entry("a-1", "001809")).id();
        UUID other = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
        assertThrows(HistoryNotFoundException.class, () -> history.find(other, id));
        assertThrows(HistoryNotFoundException.class, () -> history.find(userId, 999));
    }
}
