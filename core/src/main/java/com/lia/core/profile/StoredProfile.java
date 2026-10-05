package com.lia.core.profile;

import java.time.OffsetDateTime;

/** 저장된 프로필 — 속성 + 프로필 동의(처리방침 버전·일시) + 마지막 수정 시각. */
public record StoredProfile(
        UserProfile profile,
        String policyVersion,
        OffsetDateTime consentedAt,
        OffsetDateTime updatedAt) {
}
