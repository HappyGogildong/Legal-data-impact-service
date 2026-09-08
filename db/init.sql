-- 입법 영향 분석기 — DB 초기화 (로컬 도커 / RDS 공용)
-- 이미지: pgvector/pgvector:pg16   |   RDS: PostgreSQL 15.2+ 에서 동일하게 동작
-- 최초 기동 시 1회 실행(docker-entrypoint-initdb.d). RDS는 수동/마이그레이션으로 적용.

-- pgvector 확장만 여기서 보장한다(초기화 시 superuser 로 안전하게 CREATE EXTENSION).
--   · vector_store 테이블·HNSW 인덱스 → Spring AI PgVectorStore(initialize-schema:true)가 소유·생성
--   · law_versions 등 도메인 스키마    → Flyway(classpath:db/migration)가 소유·마이그레이션
-- 스키마 이중관리를 피하려 여기서는 테이블을 만들지 않는다(구 embedding 테이블 제거, 2026-09 정리).
CREATE EXTENSION IF NOT EXISTS vector;
