package com.lia.core.profile;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 자기신고 프로필 속성(D41·D61) — 슬라이스 ③에서 프롬프트 {@code <profile>} 에 그대로 들어갈 값.
 *
 * <p>userId·타임스탬프는 넣지 않는다(Store 키·메타데이터는 {@link StoredProfile}). 전부 선택 입력.
 * 설계: docs/components/profile/UserProfile.md, 스키마 SSOT: component-specs §2.
 */
public record UserProfile(
        Set<Purpose> purposes,
        Integer age,
        Occupation occupation,
        EmploymentType employmentType,
        HouseholdType householdType,
        HousingType housingType,
        Sido regionSido) {

    public static final int MIN_AGE = 14;   // 만 14세 이상 전용(D61)
    public static final int MAX_AGE = 120;

    public UserProfile {
        // EnumSet: 중복 제거 + 선언 순서 고정 — 속성 해시 캐시 키(D51)가 입력 순서에 흔들리지 않게
        purposes = purposes == null || purposes.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(purposes));
        if (age != null && (age < MIN_AGE || age > MAX_AGE)) {
            throw new IllegalArgumentException(
                    "age는 " + MIN_AGE + "~" + MAX_AGE + " 사이 정수여야 합니다: " + age);
        }
    }

    /** 동의만 하고 아직 입력하지 않은 프로필. */
    public static UserProfile empty() {
        return new UserProfile(null, null, null, null, null, null, null);
    }

    /**
     * 프롬프트·검색어용 "항목: 라벨" 줄 — 속성만(userId 없음, D41). 없는 항목은 생략하고 각 줄은 줄바꿈으로 끝난다.
     * Layer B 프롬프트의 [PROFILE] 블록(D69)과 법령 발견의 프로필 검색어(D70)가 같은 형식을 쓴다.
     */
    public String labelLines() {
        StringBuilder sb = new StringBuilder();
        if (!purposes.isEmpty()) {
            sb.append("관심 분야: ")
              .append(purposes.stream().map(Labeled::label).collect(Collectors.joining(", ")))
              .append('\n');
        }
        if (age != null) sb.append("나이: ").append(age).append('\n');
        appendLabel(sb, "직업", occupation);
        appendLabel(sb, "고용 형태", employmentType);
        appendLabel(sb, "가구", householdType);
        appendLabel(sb, "주거", housingType);
        appendLabel(sb, "거주 시도", regionSido);
        return sb.toString();
    }

    private static void appendLabel(StringBuilder sb, String name, Labeled value) {
        if (value != null) sb.append(name).append(": ").append(value.label()).append('\n');
    }
}
