package com.lia.core.application.bookmark;

/** 사용자당 북마크 한도 초과(D72 ④) — web 이 409 {@code bookmark_limit} 로 매핑. 메시지는 화면에 그대로 나간다. */
public class BookmarkLimitException extends RuntimeException {

    public BookmarkLimitException(int maxPerUser) {
        super("북마크는 최대 " + maxPerUser + "개까지 저장할 수 있어요.");
    }
}
