-- 계정 (Auth, D60) — 소셜 OAuth2 신원 → 내부 userId 매핑. 최소 PII(D41).
-- 성명·비밀번호 미저장. email 은 알림 채널(동의 시·IdP 제공 시, nullable).
CREATE TABLE accounts (
    user_id     uuid        PRIMARY KEY,        -- 내부 계정 키(버전 불변). 프롬프트 미주입(D41·D10)
    provider    text        NOT NULL,           -- kakao | naver | google
    provider_id text        NOT NULL,           -- IdP opaque subject
    email       text,                           -- 알림 발송용(nullable — Kakao 등 미제공 가능)
    created_at  timestamptz NOT NULL,
    UNIQUE (provider, provider_id)              -- 한 (provider, subject) → 하나의 userId (재로그인 멱등)
);
