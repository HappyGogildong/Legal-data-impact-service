-- 하루 개인화 분석 한도(D69) — 사용자·서울 날짜별 Layer B 호출 **횟수만**. 질문·답·프로필은 저장하지 않는다(D41).
-- 계정 삭제 시 함께 지운다(cascade). 차감은 upsert 한 문장(WHERE count < 한도)으로 원자적.
CREATE TABLE personalization_usage_daily (
    user_id     uuid    NOT NULL REFERENCES accounts (user_id) ON DELETE CASCADE,
    usage_date  date    NOT NULL,   -- 서울 날짜
    count       integer NOT NULL,
    PRIMARY KEY (user_id, usage_date)
);
