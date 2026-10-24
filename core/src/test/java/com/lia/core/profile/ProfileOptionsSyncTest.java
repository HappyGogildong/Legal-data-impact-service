package com.lia.core.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * 웹 프로필 선택지(web/src/lib/profile-options.json)가 enum 라벨과 같은지 — 목록 API 대신 프런트 상수를 두는 대가(screen-plan S09).
 * enum 을 바꾸면 이 테스트가 깨지고, JSON 을 함께 고친다.
 */
class ProfileOptionsSyncTest {

    static final Path OPTIONS = Path.of("..", "web", "src", "lib", "profile-options.json");   // gradle 작업 디렉터리 = core/

    @Test
    @DisplayName("웹 선택지 = enum 라벨(선언 순)")
    @SuppressWarnings("unchecked")
    void 선택지_동기() throws Exception {
        Map<String, List<String>> options = new ObjectMapper()
                .readValue(Files.readString(OPTIONS, StandardCharsets.UTF_8), LinkedHashMap.class);

        assertEquals(labels(Purpose.values()), options.get("purposes"), "purposes");
        assertEquals(labels(Occupation.values()), options.get("occupation"), "occupation");
        assertEquals(labels(EmploymentType.values()), options.get("employmentType"), "employmentType");
        assertEquals(labels(HouseholdType.values()), options.get("householdType"), "householdType");
        assertEquals(labels(HousingType.values()), options.get("housingType"), "housingType");
        assertEquals(labels(Sido.values()), options.get("regionSido"), "regionSido");
        assertEquals(6, options.size(), "모르는 키가 없어야 한다");
    }

    private static List<String> labels(Labeled[] values) {
        return Arrays.stream(values).map(Labeled::label).toList();
    }
}
