package com.lia.core.application.profile;

import java.util.Optional;
import java.util.UUID;

import com.lia.core.pipeline.dispatch.Personalization;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfile;
import com.lia.core.profile.UserProfileStore;

/**
 * Layer B 에 프로필을 넣어도 되는가(D69, UserProfile Layer B 소비 규칙) — 현재 처리방침 버전 동의 + 속성 ≥ 1.
 * 동의 버전 판정은 {@link ProfileUseCase#consentStatus}의 {@code upToDate}와 같은 기준(같은 설정값).
 * 결과에는 프로필 속성만 싣는다 — userId 는 한도 차감 키로만(D41).
 */
public class PersonalizationGate {

    private final UserProfileStore store;
    private final String currentPolicyVersion;

    public PersonalizationGate(UserProfileStore store, String currentPolicyVersion) {
        if (currentPolicyVersion == null || currentPolicyVersion.isBlank()) {
            throw new IllegalArgumentException("처리방침 버전(lia.privacy.policy-version)이 비어 있습니다.");
        }
        this.store = store;
        this.currentPolicyVersion = currentPolicyVersion;
    }

    public Personalization check(Optional<UUID> userId) {
        if (userId.isEmpty()) return Personalization.anonymous();
        Optional<StoredProfile> stored = store.find(userId.get());
        if (stored.isEmpty()) return new Personalization.Blocked(Unmet.Reason.PROFILE_REQUIRED);
        if (!currentPolicyVersion.equals(stored.get().policyVersion())) {
            return new Personalization.Blocked(Unmet.Reason.CONSENT_OUTDATED);
        }
        UserProfile profile = stored.get().profile();
        if (profile.equals(UserProfile.empty())) return new Personalization.Blocked(Unmet.Reason.PROFILE_REQUIRED);
        return new Personalization.Usable(userId.get(), profile);
    }
}
