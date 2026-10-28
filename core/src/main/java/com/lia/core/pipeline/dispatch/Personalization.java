package com.lia.core.pipeline.dispatch;

import java.util.Objects;
import java.util.UUID;

import com.lia.core.profile.UserProfile;

/**
 * 이 요청이 Layer B(내 영향·대응 방안)를 채울 수 있는가 — {@code PersonalizationGate}(application)가 판정해 넘긴다(D69).
 * {@code Usable.userId}는 하루 한도 차감 키로만 쓴다 — 프롬프트로 가는 건 {@code profile} 속성뿐(D41).
 */
public sealed interface Personalization {

    record Usable(UUID userId, UserProfile profile) implements Personalization {
        public Usable {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(profile, "profile");
        }
    }

    /** 채울 수 없다 — 사유는 PROFILE_REQUIRED(익명·없음·빈 프로필) 또는 CONSENT_OUTDATED. */
    record Blocked(Unmet.Reason reason) implements Personalization {
        public Blocked {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 세션 없는 요청. */
    static Personalization anonymous() {
        return new Blocked(Unmet.Reason.PROFILE_REQUIRED);
    }
}
