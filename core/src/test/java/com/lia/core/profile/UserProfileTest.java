package com.lia.core.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UserProfile 불변식 + 라벨 변환(Labeled) 단위 테스트. */
class UserProfileTest {

    private static UserProfile withAge(Integer age) {
        return new UserProfile(null, age, null, null, null, null, null);
    }

    @Test
    @DisplayName("age 경계 — 14·120 허용, 13·121 거부, null 허용")
    void age_경계() {
        assertEquals(14, withAge(14).age());
        assertEquals(120, withAge(120).age());
        assertNull(withAge(null).age());
        assertThrows(IllegalArgumentException.class, () -> withAge(13));
        assertThrows(IllegalArgumentException.class, () -> withAge(121));
    }

    @Test
    @DisplayName("purposes — null 이면 빈 집합, 중복 제거, 선언 순서 고정, 불변")
    void purposes_정규화() {
        assertTrue(new UserProfile(null, null, null, null, null, null, null).purposes().isEmpty());

        var input = new LinkedHashSet<>(List.of(Purpose.기타, Purpose.생활주거, Purpose.기타));
        UserProfile profile = new UserProfile(input, null, null, null, null, null, null);

        assertEquals(List.of(Purpose.생활주거, Purpose.기타), List.copyOf(profile.purposes()), "EnumSet 선언 순서");
        assertThrows(UnsupportedOperationException.class, () -> profile.purposes().add(Purpose.교육양육));
    }

    @Test
    @DisplayName("전부 비어도 유효 — empty() 와 동등")
    void 전부비어도유효() {
        assertEquals(UserProfile.empty(), new UserProfile(null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("라벨 변환 — 특수문자 라벨·숫자 시작 라벨 왕복, null 은 null")
    void 라벨_왕복() {
        assertEquals(EmploymentType.무직은퇴, Labeled.parse(EmploymentType.class, "무직·은퇴"));
        assertEquals(HouseholdType.부부자녀, Labeled.parse(HouseholdType.class, "부부+자녀"));
        assertEquals(HouseholdType.일인, Labeled.parse(HouseholdType.class, "1인"));
        assertEquals(Purpose.관심사모니터링, Labeled.parse(Purpose.class, "관심사 모니터링"));
        assertEquals(Sido.전북특별자치도, Labeled.parse(Sido.class, "전북특별자치도"));
        assertEquals("무직·은퇴", Labeled.labelOf(EmploymentType.무직은퇴));
        assertNull(Labeled.parse(Occupation.class, null));
        assertNull(Labeled.labelOf(null));
    }

    @Test
    @DisplayName("모르는 라벨·시도 약칭은 거부(400 이 되도록 IllegalArgumentException)")
    void 라벨_거부() {
        assertThrows(IllegalArgumentException.class, () -> Labeled.parse(Occupation.class, "개발자"));
        assertThrows(IllegalArgumentException.class, () -> Labeled.parse(Sido.class, "서울"));
        assertThrows(IllegalArgumentException.class, () -> Labeled.parse(Sido.class, "강남구"));
    }

    @Test
    @DisplayName("시도는 정확히 17개")
    void 시도_17개() {
        assertEquals(17, Sido.values().length);
    }
}
