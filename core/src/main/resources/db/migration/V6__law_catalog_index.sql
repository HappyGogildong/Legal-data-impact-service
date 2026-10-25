-- 법령 목록(홈 곧 시행·S06, D65) — 상태 조건 + 시행일·lawId 정렬·키셋 커서를 한 인덱스로.
CREATE INDEX idx_law_versions_status_efyd ON law_versions (status, effective_date, law_id);
