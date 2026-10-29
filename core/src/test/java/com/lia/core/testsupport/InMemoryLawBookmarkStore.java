package com.lia.core.testsupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lia.core.store.LawBookmarkStore;

/**
 * 테스트용 인메모리 북마크 저장소 — JdbcClient 없이 공개 메서드를 대체. 단일 스레드 테스트 전용.
 * {@code laws} 는 법령별 "보여 줄 버전"(bookmarkedAt 은 무시하고 추가 시각으로 바꾼다).
 */
public class InMemoryLawBookmarkStore extends LawBookmarkStore {

    private final InMemoryAccountStore accounts;
    private final Map<String, BookmarkedLaw> laws;
    private final Map<UUID, LinkedHashMap<String, OffsetDateTime>> byUser = new HashMap<>();
    private long clock = 0;

    public InMemoryLawBookmarkStore(InMemoryAccountStore accounts, int maxPerUser, Map<String, BookmarkedLaw> laws) {
        super(null, null, maxPerUser);
        this.accounts = accounts;
        this.laws = laws;
    }

    @Override
    public AddResult add(UUID userId, String lawId) {
        if (accounts.find(userId).isEmpty()) return AddResult.ACCOUNT_NOT_FOUND;
        LinkedHashMap<String, OffsetDateTime> mine = byUser.computeIfAbsent(userId, id -> new LinkedHashMap<>());
        if (mine.containsKey(lawId)) return AddResult.ALREADY;
        if (!laws.containsKey(lawId)) return AddResult.LAW_NOT_FOUND;
        if (mine.size() >= maxPerUser()) return AddResult.LIMIT;
        mine.put(lawId, OffsetDateTime.of(2026, 10, 30, 0, 0, 0, 0, ZoneOffset.UTC).plusSeconds(clock++));
        return AddResult.ADDED;
    }

    @Override
    public void remove(UUID userId, String lawId) {
        byUser.getOrDefault(userId, new LinkedHashMap<>()).remove(lawId);
    }

    @Override
    public List<BookmarkedLaw> list(UUID userId) {
        List<BookmarkedLaw> result = new ArrayList<>();
        byUser.getOrDefault(userId, new LinkedHashMap<>()).forEach((lawId, at) -> {
            BookmarkedLaw law = laws.get(lawId);
            result.addFirst(new BookmarkedLaw(lawId, law.title(), law.status(), law.effectiveDate(), at));
        });
        return result;
    }
}
