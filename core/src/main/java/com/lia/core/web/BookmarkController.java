package com.lia.core.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.bookmark.BookmarkUseCase;
import com.lia.core.auth.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code /api/v1/bookmarks} — 관심 법령 북마크(D72, [[service-api-spec]] §3.8). <b>HTTP 경계만</b>:
 * 세션 userId → {@link BookmarkUseCase} 위임. 404·409·401 은 ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/bookmarks")
public class BookmarkController {

    private final BookmarkUseCase bookmarks;

    public BookmarkController(BookmarkUseCase bookmarks) {
        this.bookmarks = bookmarks;
    }

    @GetMapping
    public BookmarkListResponse list(HttpServletRequest request) {
        return BookmarkListResponse.from(bookmarks.list(CurrentUser.require(request)));
    }

    /** 멱등 추가 — 이미 있어도 204. */
    @PutMapping("/{lawId}")
    public ResponseEntity<Void> add(@PathVariable String lawId, HttpServletRequest request) {
        bookmarks.add(CurrentUser.require(request), lawId);
        return ResponseEntity.noContent().build();
    }

    /** 멱등 해제 — 없어도 204. */
    @DeleteMapping("/{lawId}")
    public ResponseEntity<Void> remove(@PathVariable String lawId, HttpServletRequest request) {
        bookmarks.remove(CurrentUser.require(request), lawId);
        return ResponseEntity.noContent().build();
    }
}
