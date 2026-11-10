-- 유료 모델 호출 기록(D83 Token & Cost) — 호출 1회 1행. 비용은 저장하지 않고 단가 표로 조회할 때 계산한다.
-- user_id·질문·답변·프로필·법령ID 는 두지 않는다(최소수집 D41). 스펙: docs/components/observability/LlmCallRecorder.md
CREATE TABLE llm_calls (
    id                 bigserial   PRIMARY KEY,
    called_at          timestamptz NOT NULL,
    purpose            text        NOT NULL CHECK (purpose IN ('CLASSIFY', 'DISCOVERY_EMBED', 'INDEX_EMBED',
                                                               'SUMMARY', 'DIFF', 'PERSONALIZED', 'ACTION_REVISION')),
    model              text        NOT NULL,
    prompt_version     text,                  -- 분석 프롬프트 버전. 분류·임베딩은 비움
    group_key          uuid,                  -- AnalysisEngine.analyze 1회(재시도 묶음). 분류·임베딩은 비움
    attempt            smallint    NOT NULL DEFAULT 1,
    accepted           boolean,               -- 인용 검증을 통과해 응답에 쓰였나. 판정 없음·호출 실패는 비움
    succeeded          boolean     NOT NULL,  -- 예외 없이 결과를 냈나(API 오류·JSON 변환 실패는 false)
    requested          text,                  -- 첫 Layer B 에서 사용자에게 나갈 차원(IMPACT · ACTION · IMPACT,ACTION)
    input_tokens       integer,               -- Anthropic: 캐시 읽기·쓰기를 뺀 입력
    output_tokens      integer,               -- thinking 포함
    cache_read_tokens  integer,
    cache_write_tokens integer,
    stop_reason        text,                  -- end_turn · max_tokens … (출력 잘림률)
    context_sources    smallint               -- 주입한 근거 블록 수
);

CREATE INDEX llm_calls_called_at ON llm_calls (called_at);
CREATE INDEX llm_calls_purpose_called_at ON llm_calls (purpose, called_at);
CREATE INDEX llm_calls_group_key ON llm_calls (group_key) WHERE group_key IS NOT NULL;

-- 시점별 단가 — 공식 가격표를 보고 사람이 넣는다(처음엔 비어 있음). 바뀌면 새 effective_from 으로 한 행 더.
CREATE TABLE llm_prices (
    model           text          NOT NULL,
    token_kind      text          NOT NULL CHECK (token_kind IN ('input', 'output', 'cache_read', 'cache_write')),
    usd_per_million numeric(10,4) NOT NULL CHECK (usd_per_million >= 0),
    effective_from  date          NOT NULL,
    PRIMARY KEY (model, token_kind, effective_from)
);

-- 호출 1행 + 비용. 호출일(서울)에 유효한 단가 중 최신을 토큰 종류별로 붙인다.
-- 토큰이 있는데 단가가 없는 종류가 하나라도 있으면 cost_usd 는 NULL, priced = false(0으로 꾸미지 않는다).
CREATE VIEW llm_call_costs AS
SELECT x.*,
       CASE WHEN x.priced THEN
           (COALESCE(x.input_tokens, 0) * COALESCE(x.input_price, 0)
          + COALESCE(x.output_tokens, 0) * COALESCE(x.output_price, 0)
          + COALESCE(x.cache_read_tokens, 0) * COALESCE(x.cache_read_price, 0)
          + COALESCE(x.cache_write_tokens, 0) * COALESCE(x.cache_write_price, 0)) / 1000000
       END AS cost_usd
FROM (
    SELECT c.*, pi.usd_per_million AS input_price, po.usd_per_million AS output_price,
           pr.usd_per_million AS cache_read_price, pw.usd_per_million AS cache_write_price,
           (COALESCE(c.input_tokens, 0) = 0 OR pi.usd_per_million IS NOT NULL)
           AND (COALESCE(c.output_tokens, 0) = 0 OR po.usd_per_million IS NOT NULL)
           AND (COALESCE(c.cache_read_tokens, 0) = 0 OR pr.usd_per_million IS NOT NULL)
           AND (COALESCE(c.cache_write_tokens, 0) = 0 OR pw.usd_per_million IS NOT NULL) AS priced
    FROM llm_calls c
    LEFT JOIN LATERAL (SELECT p.usd_per_million FROM llm_prices p
                       WHERE p.model = c.model AND p.token_kind = 'input'
                         AND p.effective_from <= (c.called_at AT TIME ZONE 'Asia/Seoul')::date
                       ORDER BY p.effective_from DESC LIMIT 1) pi ON true
    LEFT JOIN LATERAL (SELECT p.usd_per_million FROM llm_prices p
                       WHERE p.model = c.model AND p.token_kind = 'output'
                         AND p.effective_from <= (c.called_at AT TIME ZONE 'Asia/Seoul')::date
                       ORDER BY p.effective_from DESC LIMIT 1) po ON true
    LEFT JOIN LATERAL (SELECT p.usd_per_million FROM llm_prices p
                       WHERE p.model = c.model AND p.token_kind = 'cache_read'
                         AND p.effective_from <= (c.called_at AT TIME ZONE 'Asia/Seoul')::date
                       ORDER BY p.effective_from DESC LIMIT 1) pr ON true
    LEFT JOIN LATERAL (SELECT p.usd_per_million FROM llm_prices p
                       WHERE p.model = c.model AND p.token_kind = 'cache_write'
                         AND p.effective_from <= (c.called_at AT TIME ZONE 'Asia/Seoul')::date
                       ORDER BY p.effective_from DESC LIMIT 1) pw ON true
) x;
