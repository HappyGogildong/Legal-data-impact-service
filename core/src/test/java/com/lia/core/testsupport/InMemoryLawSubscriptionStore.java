package com.lia.core.testsupport;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lia.core.store.LawSubscriptionStore;

/** 테스트용 인메모리 구독 저장소 — {@code laws}: lawId → (법령명, 다음 시행일 또는 null). 단일 스레드 전용. */
public class InMemoryLawSubscriptionStore extends LawSubscriptionStore {

    public record KnownLaw(String title, LocalDate nextEffectiveDate) {}

    private final InMemoryAccountStore accounts;
    private final Map<String, KnownLaw> laws;
    private final Map<UUID, LinkedHashMap<String, Integer>> byUser = new HashMap<>();

    public InMemoryLawSubscriptionStore(InMemoryAccountStore accounts, int maxPerUser, Map<String, KnownLaw> laws) {
        super(null, null, maxPerUser);
        this.accounts = accounts;
        this.laws = laws;
    }

    @Override
    public SubscribeResult subscribe(UUID userId, String lawId, int notifyBefore) {
        if (accounts.find(userId).isEmpty()) return SubscribeResult.ACCOUNT_NOT_FOUND;
        LinkedHashMap<String, Integer> mine = byUser.computeIfAbsent(userId, id -> new LinkedHashMap<>());
        if (mine.containsKey(lawId)) {
            mine.put(lawId, notifyBefore);
            return SubscribeResult.UPDATED;
        }
        if (!laws.containsKey(lawId)) return SubscribeResult.LAW_NOT_FOUND;
        if (mine.size() >= maxPerUser()) return SubscribeResult.LIMIT;
        mine.put(lawId, notifyBefore);
        return SubscribeResult.SUBSCRIBED;
    }

    @Override
    public void unsubscribe(UUID userId, String lawId) {
        LinkedHashMap<String, Integer> mine = byUser.get(userId);
        if (mine != null) mine.remove(lawId);
    }

    @Override
    public List<SubscribedLaw> list(UUID userId) {
        List<SubscribedLaw> result = new ArrayList<>();
        byUser.getOrDefault(userId, new LinkedHashMap<>()).forEach((lawId, notifyBefore) -> {
            KnownLaw law = laws.get(lawId);
            result.add(new SubscribedLaw(lawId, law.title(), notifyBefore,
                    OffsetDateTime.of(2026, 10, 31, 0, 0, 0, 0, ZoneOffset.UTC), law.nextEffectiveDate()));
        });
        result.sort(Comparator.comparing(SubscribedLaw::nextEffectiveDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }
}
