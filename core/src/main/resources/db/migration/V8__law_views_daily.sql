-- 자주 찾는 법령(D66) — 서울 날짜별 조회 **횟수만**. 누가 봤는지(userId·IP·세션)는 저장하지 않는다(D41).
-- 조회 1회 = S05 상세 방문(브라우저 조회 신호) + 분석 해소. 증가는 upsert 한 문장(D48 — 측정 전 기법 없음).
CREATE TABLE law_views_daily (
    law_id          text    NOT NULL,
    effective_date  date    NOT NULL,
    view_date       date    NOT NULL,   -- 서울 날짜
    views           integer NOT NULL,
    PRIMARY KEY (law_id, effective_date, view_date)
);

-- 구간 합산(view_date 범위) 조회
CREATE INDEX idx_law_views_daily_date ON law_views_daily (view_date);
