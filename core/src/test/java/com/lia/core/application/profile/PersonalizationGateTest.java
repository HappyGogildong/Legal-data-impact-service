package com.lia.core.application.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.dispatch.Personalization;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfile;
import com.lia.core.profile.UserProfileStore;

/** PersonalizationGate — 현재 버전 동의 + 속성 ≥ 1 인 프로필만 Layer B 에(D69). DB 없이 저장소 대체. */
class PersonalizationGateTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UserProfile FILLED = new UserProfile(null, 34, null, null, null, null, null);

    private static PersonalizationGate gate(StoredProfile stored) {
        UserProfileStore store = new UserProfileStore(null) {
            @Override
            public Optional<StoredProfile> find(UUID userId) {
                return Optional.ofNullable(stored);
            }
        };
        return new PersonalizationGate(store, "2026-12");
    }

    private static StoredProfile stored(UserProfile profile, String policyVersion) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-28T00:00:00Z");
        return new StoredProfile(profile, policyVersion, now, now);
    }

    @Test
    void 익명은_PROFILE_REQUIRED() {
        assertEquals(new Personalization.Blocked(Unmet.Reason.PROFILE_REQUIRED),
                gate(stored(FILLED, "2026-12")).check(Optional.empty()));
    }

    @Test
    void 프로필_행이_없으면_PROFILE_REQUIRED() {
        assertEquals(new Personalization.Blocked(Unmet.Reason.PROFILE_REQUIRED), gate(null).check(Optional.of(USER)));
    }

    @Test
    void 빈_프로필은_PROFILE_REQUIRED() {
        assertEquals(new Personalization.Blocked(Unmet.Reason.PROFILE_REQUIRED),
                gate(stored(UserProfile.empty(), "2026-12")).check(Optional.of(USER)));
    }

    @Test
    void 옛_버전_동의는_CONSENT_OUTDATED() {
        assertEquals(new Personalization.Blocked(Unmet.Reason.CONSENT_OUTDATED),
                gate(stored(FILLED, "2026-09")).check(Optional.of(USER)));
    }

    @Test
    void 현재_버전_동의와_속성이_있으면_Usable() {
        assertEquals(new Personalization.Usable(USER, FILLED), gate(stored(FILLED, "2026-12")).check(Optional.of(USER)));
    }
}
