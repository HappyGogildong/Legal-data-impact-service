package com.lia.core.profile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 프로필 저장소 — {@code user_profiles} 동의 기록·속성 교체·조회·파기(D41·D61, [[LawStore]] 패턴).
 *
 * <p>행은 {@link #recordConsent} 만 만든다. {@link #replaceAttributes} 는 기존 행의 속성만 바꾸고
 * 동의 컬럼은 건드리지 않으므로, 어떤 경로로도 동의 없는 행이 생기지 않는다.
 * enum 은 라벨 문자열로 저장한다. 스키마: {@code db/migration/V3__user_profiles.sql}.
 */
@Repository
public class UserProfileStore {

    private final JdbcClient jdbc;

    public UserProfileStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 프로필 동의 기록 — 행이 없으면 빈 프로필 생성, 있으면 버전·일시만 갱신(속성 보존). 재동의도 같은 호출. */
    public void recordConsent(UUID userId, String policyVersion) {
        jdbc.sql("""
                INSERT INTO user_profiles (user_id, policy_version, consented_at, updated_at)
                VALUES (:userId, :policyVersion, :now, :now)
                ON CONFLICT (user_id) DO UPDATE SET
                  policy_version = EXCLUDED.policy_version, consented_at = EXCLUDED.consented_at
                """)
                .param("userId", userId)
                .param("policyVersion", policyVersion)
                .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                .update();
    }

    /** 속성 전체 교체(보내지 않은 필드 → null) + updated_at. 동의 불변. 행이 없으면 아무것도 안 하고 false. */
    public boolean replaceAttributes(UUID userId, UserProfile profile) {
        int updated = jdbc.sql("""
                UPDATE user_profiles SET
                  purposes = CAST(:purposes AS text[]), age = :age, occupation = :occupation,
                  employment_type = :employmentType, household_type = :householdType,
                  housing_type = :housingType, region_sido = :regionSido, updated_at = :now
                WHERE user_id = :userId
                """)
                .param("purposes", profile.purposes().stream().map(Purpose::label).toArray(String[]::new))
                .param("age", profile.age())
                .param("occupation", Labeled.labelOf(profile.occupation()))
                .param("employmentType", Labeled.labelOf(profile.employmentType()))
                .param("householdType", Labeled.labelOf(profile.householdType()))
                .param("housingType", Labeled.labelOf(profile.housingType()))
                .param("regionSido", Labeled.labelOf(profile.regionSido()))
                .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                .param("userId", userId)
                .update();
        return updated == 1;
    }

    public Optional<StoredProfile> find(UUID userId) {
        return jdbc.sql("""
                SELECT purposes, age, occupation, employment_type, household_type, housing_type,
                       region_sido, policy_version, consented_at, updated_at
                FROM user_profiles WHERE user_id = :userId
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> toStoredProfile(rs))
                .optional();
    }

    /** 파기(프로필 동의 철회 겸). 멱등. */
    public void delete(UUID userId) {
        jdbc.sql("DELETE FROM user_profiles WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    private static StoredProfile toStoredProfile(ResultSet rs) throws SQLException {
        String[] purposeLabels = (String[]) rs.getArray("purposes").getArray();
        var profile = new UserProfile(
                Arrays.stream(purposeLabels)
                        .map(label -> Labeled.parse(Purpose.class, label))
                        .collect(Collectors.toSet()),
                (Integer) rs.getObject("age"),
                Labeled.parse(Occupation.class, rs.getString("occupation")),
                Labeled.parse(EmploymentType.class, rs.getString("employment_type")),
                Labeled.parse(HouseholdType.class, rs.getString("household_type")),
                Labeled.parse(HousingType.class, rs.getString("housing_type")),
                Labeled.parse(Sido.class, rs.getString("region_sido")));
        return new StoredProfile(
                profile,
                rs.getString("policy_version"),
                rs.getObject("consented_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
