-- 입법 영향 분석기 — DB 초기화 (로컬 도커 / RDS 공용)
-- 이미지: pgvector/pgvector:pg16   |   RDS: PostgreSQL 15.2+ 에서 동일하게 동작
-- 최초 기동 시 1회 실행(docker-entrypoint-initdb.d). RDS는 수동/마이그레이션으로 적용.

-- 1) pgvector 확장 (단일 Postgres 안에서 벡터 검색 — 별도 벡터DB 아님, ADR-001)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2) 임베딩 테이블 (파이프라인 소유: RAG Indexer 적재 / 검색)
--    분석용(namespace='law')·탐색용(namespace='bill') 2 네임스페이스 공용.
--    dim 1536 = 임베딩 모델 결정(D32: OpenAI text-embedding-3-small 기준)과 일치.
--    ⚠️ 벤더/차원 변경(예: Upstage 4096) 시 컬럼·인덱스 재생성 + 전 코퍼스 재색인 필요.
CREATE TABLE IF NOT EXISTS embedding (
    id          bigserial     PRIMARY KEY,
    namespace   text          NOT NULL,                       -- 'law' | 'bill'
    source_id   text          NOT NULL,                       -- 인용키: LAW:{id}:art:{no} / BILL:{billNo}
    content     text          NOT NULL,                       -- 임베딩 원문 스니펫
    meta        jsonb         NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(1536)  NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    UNIQUE (namespace, source_id)
);

-- 코사인 거리 HNSW 인덱스 (근사 최근접 검색)
CREATE INDEX IF NOT EXISTS idx_embedding_hnsw
    ON embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_embedding_ns
    ON embedding (namespace);

-- 3) 관계형 도메인(bill / article / bill_facts / impact_result)은
--    Spring(core, JPA/Flyway)이 소유·마이그레이션한다. 여기서는 만들지 않음(스키마 이중관리 방지).
--    파이프라인은 임베딩 테이블을 소유하고, 도메인 테이블은 core 스키마를 공유·읽기.
