package com.lia.core.testsupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.lia.core.auth.Account;
import com.lia.core.auth.AccountStore;

/** 테스트용 인메모리 AccountStore — JdbcClient 없이 모든 공개 메서드를 대체. 단일 스레드 테스트 전용. */
public class InMemoryAccountStore extends AccountStore {

    private final Map<UUID, Account> byUserId = new HashMap<>();
    private int creates = 0;

    public InMemoryAccountStore() {
        super(null);
    }

    /** findOrCreate 가 실제로 새 계정을 만든 횟수. */
    public int creates() {
        return creates;
    }

    @Override
    public Optional<Account> findByProvider(String provider, String providerId) {
        return byUserId.values().stream()
                .filter(account -> account.provider().equals(provider) && account.providerId().equals(providerId))
                .findFirst();
    }

    @Override
    public Optional<Account> find(UUID userId) {
        return Optional.ofNullable(byUserId.get(userId));
    }

    @Override
    public Account findOrCreate(String provider, String providerId) {
        return findByProvider(provider, providerId).orElseGet(() -> {
            creates++;
            var account = new Account(UUID.randomUUID(), provider, providerId, null, null,
                    OffsetDateTime.now(ZoneOffset.UTC));
            byUserId.put(account.userId(), account);
            return account;
        });
    }

    @Override
    public void setNotificationEmail(UUID userId, String email) {
        find(userId).ifPresent(account -> byUserId.put(userId, new Account(account.userId(), account.provider(),
                account.providerId(), email, OffsetDateTime.now(ZoneOffset.UTC), account.createdAt())));
    }

    @Override
    public void clearNotificationEmail(UUID userId) {
        find(userId).ifPresent(account -> byUserId.put(userId, new Account(account.userId(), account.provider(),
                account.providerId(), null, null, account.createdAt())));
    }

    @Override
    public void delete(UUID userId) {
        byUserId.remove(userId);
    }
}
