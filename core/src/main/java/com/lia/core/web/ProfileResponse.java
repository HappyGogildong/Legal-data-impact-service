package com.lia.core.web;

import java.time.OffsetDateTime;
import java.util.List;

import com.lia.core.profile.Labeled;
import com.lia.core.profile.Purpose;
import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfile;

/** 프로필 응답 — 속성 라벨 + updatedAt. userId 를 담을 필드 자체가 없다(D41). */
public record ProfileResponse(
        List<String> purposes,
        Integer age,
        String occupation,
        String employmentType,
        String householdType,
        String housingType,
        String regionSido,
        OffsetDateTime updatedAt) {

    static ProfileResponse from(StoredProfile stored) {
        UserProfile profile = stored.profile();
        return new ProfileResponse(
                profile.purposes().stream().map(Purpose::label).toList(),
                profile.age(),
                Labeled.labelOf(profile.occupation()),
                Labeled.labelOf(profile.employmentType()),
                Labeled.labelOf(profile.householdType()),
                Labeled.labelOf(profile.housingType()),
                Labeled.labelOf(profile.regionSido()),
                stored.updatedAt());
    }
}
