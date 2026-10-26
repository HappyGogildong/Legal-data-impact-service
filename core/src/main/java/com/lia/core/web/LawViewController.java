package com.lia.core.web;

import java.time.DateTimeException;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.store.LawViewStore;
import com.lia.core.store.TrendingWindow;

/**
 * 자주 찾는 법령 — {@code GET /api/v1/laws/trending}·조회 신호 {@code POST /api/v1/laws/{lawId}/{effectiveDate}/views}
 * ([[service-api-spec]] §3.5, D66). HTTP 경계만: 검증 400 · 없는 버전 404. 익명 공개, 조회 신호는 CSRF 예외(세션 안 읽음).
 * 설계: docs/components/web/TrendingApi.md
 */
@RestController
@RequestMapping("/api/v1/laws")
public class LawViewController {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;

    private final LawViewStore views;

    public LawViewController(LawViewStore views) {
        this.views = views;
    }

    @GetMapping("/trending")
    public LawViewStore.TrendingPage trending(@RequestParam(required = false) String window,
                                              @RequestParam(required = false) Integer limit) {
        TrendingWindow parsed = window == null ? TrendingWindow.week : TrendingWindow.parse(window);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new IllegalArgumentException("limit 은 1~" + MAX_LIMIT + " 입니다.");
        }
        return views.trending(parsed, size);
    }

    /** S05 조회 신호 — 횟수 +1(서울 오늘). 누가 봤는지는 남기지 않는다(D41). */
    @PostMapping("/{lawId}/{effectiveDate}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> recordView(@PathVariable String lawId, @PathVariable String effectiveDate) {
        LocalDate date;
        try {
            date = LocalDate.parse(effectiveDate);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("effectiveDate 는 YYYY-MM-DD 형식이어야 합니다.", e);
        }
        if (!views.record(lawId, date)) {
            throw new LawNotFoundException(lawId + "@" + effectiveDate);
        }
        return ResponseEntity.noContent().build();
    }
}
