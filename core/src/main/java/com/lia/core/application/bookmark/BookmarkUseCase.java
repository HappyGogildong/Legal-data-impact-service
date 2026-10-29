package com.lia.core.application.bookmark;

import java.util.List;
import java.util.UUID;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.store.LawBookmarkStore;
import com.lia.core.store.LawBookmarkStore.BookmarkedLaw;

/**
 * 관심 법령 북마크 유스케이스(D72) — 저장소의 추가 결과를 예외로 바꾼다. 규칙(멱등·한도·잠금)은 저장소에 있다.
 * Spring 애노테이션 없음: {@code config/UserConfig} 의 {@code @Bean}. 설계: docs/components/web/BookmarkApi.md
 */
public class BookmarkUseCase {

    private final LawBookmarkStore store;

    public BookmarkUseCase(LawBookmarkStore store) {
        this.store = store;
    }

    /**
     * 멱등 — 이미 있으면 그대로.
     *
     * @throws UnknownLawException      저장된 버전이 없는 법령
     * @throws BookmarkLimitException   한도 초과
     * @throws AccountNotFoundException 세션이 가리키는 계정이 없음 — web 이 401 로 매핑
     */
    public void add(UUID userId, String lawId) {
        switch (store.add(userId, lawId)) {
            case ADDED, ALREADY -> { }
            case LAW_NOT_FOUND -> throw new UnknownLawException(lawId);
            case LIMIT -> throw new BookmarkLimitException(store.maxPerUser());
            case ACCOUNT_NOT_FOUND -> throw new AccountNotFoundException();
        }
    }

    /** 멱등. */
    public void remove(UUID userId, String lawId) {
        store.remove(userId, lawId);
    }

    /** 추가한 시각 최근 순, 법령마다 보여 줄 버전. */
    public List<BookmarkedLaw> list(UUID userId) {
        return store.list(userId);
    }
}
