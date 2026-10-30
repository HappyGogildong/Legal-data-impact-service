package com.lia.core.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.lia.core.store.LawBookmarkStore.BookmarkedLaw;

/** {@code GET /api/v1/bookmarks} 응답(D72). 상태는 화면 라벨 셋으로 접는다 — 시행중·과거 → 시행됨. */
public record BookmarkListResponse(List<BookmarkItem> items) {

    public enum BookmarkStatus { 시행예정, 시행됨, 철회 }

    /** @param effectiveDate 보여 줄 버전의 시행일 — S05 경로 {@code /laws/{lawId}/{effectiveDate}} */
    public record BookmarkItem(String lawId, String title, BookmarkStatus status, LocalDate effectiveDate,
                               OffsetDateTime bookmarkedAt) {

        static BookmarkItem from(BookmarkedLaw law) {
            BookmarkStatus status = switch (law.status()) {
                case 시행예정 -> BookmarkStatus.시행예정;
                case 시행중, 과거 -> BookmarkStatus.시행됨;
                case 철회 -> BookmarkStatus.철회;
            };
            return new BookmarkItem(law.lawId(), law.title(), status, law.effectiveDate(), law.bookmarkedAt());
        }
    }

    static BookmarkListResponse from(List<BookmarkedLaw> laws) {
        return new BookmarkListResponse(laws.stream().map(BookmarkItem::from).toList());
    }
}
