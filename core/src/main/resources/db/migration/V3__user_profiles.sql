-- 자기신고 프로필(D41) + 프로필 수집 동의(D61). 1행 = 한 계정의 프로필.
-- 행은 동의가 만든다(속성은 빈 채로) → 동의 컬럼 NOT NULL = "동의 없는 프로필"을 DB가 거부.
-- enum 컬럼은 라벨 문자열(component-specs §2). 값 검증은 도메인(UserProfile·Labeled)이 한다.
CREATE TABLE user_profiles (
    user_id         uuid        PRIMARY KEY REFERENCES accounts (user_id) ON DELETE CASCADE,  -- 계정 파기 시 함께 삭제
    purposes        text[]      NOT NULL DEFAULT '{}',
    age             int         CHECK (age BETWEEN 14 AND 120),   -- 만 14세 이상 전용(D61). 도메인 검증의 방어선
    occupation      text,
    employment_type text,
    household_type  text,
    housing_type    text,
    region_sido     text,                                         -- 17개 시도 공식 명칭
    policy_version  text        NOT NULL,                         -- 동의한 처리방침 버전
    consented_at    timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL
);
