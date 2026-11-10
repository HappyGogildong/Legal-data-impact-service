-- 질의 이벤트(D83 2단계) — 분석 요청·상황 반영 재생성 1건 1행. 결과의 모양만 남긴다.
-- 질문 원문·user_id·프로필·법령ID 는 두지 않는다(최소수집 D41). 법령별 조회 수는 law_views_daily, 토큰은 llm_calls.
-- 스펙: docs/components/observability/QueryEventRecorder.md
CREATE TABLE query_events (
    id               bigserial   PRIMARY KEY,
    occurred_at      timestamptz NOT NULL,
    kind             text        NOT NULL CHECK (kind IN ('ANALYZE', 'ACTION_REVISION')),
    status           text        NOT NULL CHECK (status IN ('ANSWERED', 'NOT_ANSWERED', 'UNRESOLVED',
                                                            'ANONYMOUS_LIMIT', 'ERROR')),
    signed_in        boolean     NOT NULL,
    route            text        CHECK (route IN ('REFERENCE', 'DISCOVERY')),   -- 미해소·익명 상한·예외는 비움
    resolution_state text,                  -- OFF_TOPIC · AMBIGUOUS · NOT_FOUND_YET · UNVERIFIED (미해소만)
    primary_type     text,                  -- 화면이 먼저 보여 줄 주 의도
    types            text,                  -- 채워야 할 차원 전체(QueryType 선언 순, 쉼표 구분)
    filled           text,                  -- 채운 차원
    unmet            jsonb       NOT NULL DEFAULT '{}',   -- 못 채운 차원 → Unmet.Reason
    article_named    boolean,               -- 법령 지목 경로에서 조문을 지목했나
    candidates       smallint,              -- 발견 후보 수 또는 비슷한 이름 후보 수
    profile_search   boolean,               -- "내 프로필 기준으로 찾기"(첫 분석만)
    prompt_version   text
);

CREATE INDEX query_events_occurred_at ON query_events (occurred_at);
CREATE INDEX query_events_status_occurred_at ON query_events (status, occurred_at);
