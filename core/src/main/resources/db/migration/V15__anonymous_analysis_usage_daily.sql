-- 익명 분석 요청의 서비스 전체 하루 상한(D81) — 서울 날짜별 횟수만. 질문·IP 등 요청 내용은 두지 않는다.
-- 차감은 upsert 한 문장(WHERE count < 상한)으로 원자적.
CREATE TABLE anonymous_analysis_usage_daily (
    usage_date  date    PRIMARY KEY,   -- 서울 날짜
    count       integer NOT NULL
);
