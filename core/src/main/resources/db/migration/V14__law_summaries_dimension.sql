-- Layer A 선계산에 변경점(DIFF) 추가(D80) — 한 버전에 차원마다 1행. 기존 행은 모두 쉬운 요약이다.
-- 변경점은 대조한 기준선까지 같아야 유효하므로 baseline_revision 을 함께 둔다(제정·요약은 NULL).
ALTER TABLE law_summaries ADD COLUMN dimension text NOT NULL DEFAULT 'SUMMARY' CHECK (dimension IN ('SUMMARY', 'DIFF'));
ALTER TABLE law_summaries ALTER COLUMN dimension DROP DEFAULT;
ALTER TABLE law_summaries ADD COLUMN baseline_revision text;
ALTER TABLE law_summaries DROP CONSTRAINT law_summaries_pkey;
ALTER TABLE law_summaries ADD PRIMARY KEY (law_id, effective_date, dimension);
