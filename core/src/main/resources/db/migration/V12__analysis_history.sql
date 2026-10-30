-- 질의·분석 이력(D73) — 명시적 저장만, 서버가 보관한 결과로 만든 스냅샷. 계정 삭제 시 함께 지운다(cascade, D41).
CREATE TABLE analysis_history (
    id             bigserial   PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES accounts (user_id) ON DELETE CASCADE,
    analysis_id    text        NOT NULL,   -- 세션의 analysisId — 같은 결과의 중복 저장 방지
    query          text        NOT NULL,
    law_id         text        NOT NULL,
    effective_date date        NOT NULL,
    law_title      text        NOT NULL,
    law_revision   text        NOT NULL,   -- 저장 당시 그 버전의 revision — "법령 갱신됨" 판정
    law_status     text        NOT NULL,   -- 저장 당시 그 버전의 상태
    snapshot       jsonb       NOT NULL,   -- { body, revision?, answers }
    created_at     timestamptz NOT NULL,
    UNIQUE (user_id, analysis_id)
);
CREATE INDEX idx_analysis_history_user_created ON analysis_history (user_id, created_at DESC);
