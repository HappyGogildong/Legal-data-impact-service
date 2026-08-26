-- Law Store 정본 (D54) — 1행 = 법령 한 버전. payload jsonb 에 Law 애그리거트 전체.
-- 벡터 chunks 는 RAGIndexer 단계에서 PgVectorStore 가 별도 관리(V2+).
CREATE TABLE law_versions (
    law_id          text        NOT NULL,   -- 법령ID (버전 불변, 연결키)
    effective_date  date        NOT NULL,   -- 시행일 (버전 식별, D43)
    revision        text        NOT NULL,   -- 분석영향 필드 해시 (캐시 무효화, D16)
    status          text        NOT NULL,   -- 시행중 | 시행예정
    title           text        NOT NULL,
    baseline_law_id text,                   -- diff 기준선 연결(있으면)
    payload         jsonb       NOT NULL,   -- Law 애그리거트 전체(조문·부칙·개정문…)
    last_seen       timestamptz NOT NULL,
    PRIMARY KEY (law_id, effective_date)    -- 정본 단위 (lawId, efYd) — lawId 단독 아님(D43)
);

-- baseline 조회(같은 lawId 의 시행중본) · 상태 필터
CREATE INDEX idx_law_versions_lawid_status ON law_versions (law_id, status);
