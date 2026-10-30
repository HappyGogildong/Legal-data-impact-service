package com.lia.core.application.bookmark;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.domain.law.Law;
import com.lia.core.store.LawBookmarkStore.BookmarkedLaw;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryLawBookmarkStore;

/** 북마크 유스케이스 — 저장소 결과 → 예외(없는 법령·한도·계정 없음), 멱등. */
class BookmarkUseCaseTest {

    static final Map<String, BookmarkedLaw> LAWS = Map.of(
            "A", new BookmarkedLaw("A", "가법", Law.Status.시행예정, LocalDate.of(2026, 11, 1), null),
            "B", new BookmarkedLaw("B", "나법", Law.Status.시행중, LocalDate.of(2026, 8, 4), null));

    InMemoryAccountStore accounts;
    BookmarkUseCase bookmarks;
    UUID userId;

    @BeforeEach
    void setup() {
        accounts = new InMemoryAccountStore();
        bookmarks = new BookmarkUseCase(new InMemoryLawBookmarkStore(accounts, 1, LAWS));
        userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
    }

    @Test
    void 추가는_멱등() {
        bookmarks.add(userId, "A");
        bookmarks.add(userId, "A");
        assertEquals(1, bookmarks.list(userId).size());
    }

    @Test
    void 없는_법령이면_UnknownLawException() {
        assertThrows(UnknownLawException.class, () -> bookmarks.add(userId, "Z"));
    }

    @Test
    void 한도를_넘으면_BookmarkLimitException_메시지에_한도() {
        bookmarks.add(userId, "A");
        BookmarkLimitException e = assertThrows(BookmarkLimitException.class, () -> bookmarks.add(userId, "B"));
        assertEquals("북마크는 최대 1개까지 저장할 수 있어요.", e.getMessage());
    }

    @Test
    void 계정이_없으면_AccountNotFoundException() {
        assertThrows(AccountNotFoundException.class, () -> bookmarks.add(UUID.randomUUID(), "A"));
    }

    @Test
    void 해제는_멱등() {
        bookmarks.remove(userId, "A");
        bookmarks.add(userId, "A");
        bookmarks.remove(userId, "A");
        assertTrue(bookmarks.list(userId).isEmpty());
    }
}
