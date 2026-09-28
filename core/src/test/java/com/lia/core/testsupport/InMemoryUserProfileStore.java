package com.lia.core.testsupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfile;
import com.lia.core.profile.UserProfileStore;

/** 테스트용 인메모리 UserProfileStore — 실 store 와 같은 계약(동의가 행을 만들고, 교체는 동의 불변). */
public class InMemoryUserProfileStore extends UserProfileStore {

    private final Map<UUID, StoredProfile> rows = new HashMap<>();

    public InMemoryUserProfileStore() {
        super(null);
    }

    /** 인메모리는 accounts 를 모른다 — 계정 존재 여부와 무관하게 항상 true(실 store 는 계정 없으면 false). */
    @Override
    public boolean recordConsent(UUID userId, String policyVersion) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        StoredProfile current = rows.get(userId);
        rows.put(userId, current == null
                ? new StoredProfile(UserProfile.empty(), policyVersion, now, now)
                : new StoredProfile(current.profile(), policyVersion, now, current.updatedAt()));
        return true;
    }

    @Override
    public boolean replaceAttributes(UUID userId, UserProfile profile) {
        StoredProfile current = rows.get(userId);
        if (current == null) return false;
        rows.put(userId, new StoredProfile(profile, current.policyVersion(), current.consentedAt(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        return true;
    }

    @Override
    public Optional<StoredProfile> find(UUID userId) {
        return Optional.ofNullable(rows.get(userId));
    }

    @Override
    public void delete(UUID userId) {
        rows.remove(userId);
    }
}
