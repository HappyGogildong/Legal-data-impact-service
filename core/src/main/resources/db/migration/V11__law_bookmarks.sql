-- 관심 법령 북마크(D72) — 법령 단위(lawId). 보관만, 알림 없음. 계정 삭제 시 함께 지운다(cascade, D41).
CREATE TABLE law_bookmarks (
    user_id    uuid        NOT NULL REFERENCES accounts (user_id) ON DELETE CASCADE,
    law_id     text        NOT NULL,   -- law_versions 의 키는 (law_id, effective_date) 라 외래키를 걸지 않는다
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, law_id)
);
CREATE INDEX idx_law_bookmarks_user_created ON law_bookmarks (user_id, created_at DESC);
