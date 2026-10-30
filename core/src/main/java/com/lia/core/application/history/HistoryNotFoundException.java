package com.lia.core.application.history;

/** 이력이 없거나 남의 기록 — web 이 404 {@code not_found} 로 매핑(존재 자체를 숨긴다). */
public class HistoryNotFoundException extends RuntimeException {

    public HistoryNotFoundException(long id) {
        super("이력을 찾을 수 없습니다: " + id);
    }
}
