package com.lia.core.web;

import java.util.List;
import java.util.stream.Collectors;

import com.lia.core.profile.EmploymentType;
import com.lia.core.profile.HouseholdType;
import com.lia.core.profile.HousingType;
import com.lia.core.profile.Labeled;
import com.lia.core.profile.Occupation;
import com.lia.core.profile.Purpose;
import com.lia.core.profile.Sido;
import com.lia.core.profile.UserProfile;

/**
 * {@code PUT /api/v1/profile} 바디 — 값은 라벨 문자열(component-specs §2). 전부 선택 입력, 전체 교체.
 * 모르는 라벨·범위 밖 age 는 IllegalArgumentException → 400.
 */
public record ProfileApiRequest(
        List<String> purposes,
        Integer age,
        String occupation,
        String employmentType,
        String householdType,
        String housingType,
        String regionSido) {

    UserProfile toProfile() {
        return new UserProfile(
                purposes == null ? null : purposes.stream().map(ProfileApiRequest::purpose).collect(Collectors.toSet()),
                age,
                Labeled.parse(Occupation.class, occupation),
                Labeled.parse(EmploymentType.class, employmentType),
                Labeled.parse(HouseholdType.class, householdType),
                Labeled.parse(HousingType.class, housingType),
                Labeled.parse(Sido.class, regionSido));
    }

    private static Purpose purpose(String label) {
        if (label == null) throw new IllegalArgumentException("purposes 에 null 을 넣을 수 없습니다.");
        return Labeled.parse(Purpose.class, label);
    }
}
