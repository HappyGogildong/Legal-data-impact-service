package com.lia.core.store;

/** 자주 찾는 법령 집계 구간 — 오늘(서울) 포함 일 수. 이름은 API 값 그대로(day·week·month). */
public enum TrendingWindow {
    day(1), week(7), month(30);

    private final int days;

    TrendingWindow(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }

    /** API 값 → 구간. 그 밖은 {@link IllegalArgumentException}(→ 400). */
    public static TrendingWindow parse(String value) {
        for (TrendingWindow window : values()) {
            if (window.name().equals(value)) return window;
        }
        throw new IllegalArgumentException("window 는 day·week·month 중 하나입니다.");
    }
}
