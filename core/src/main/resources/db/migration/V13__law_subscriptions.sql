-- 법령 구독(D74) — 법령 단위(lawId), 알림 시점 하나. 계정 삭제 시 함께 지운다(cascade, D41).
CREATE TABLE law_subscriptions (
    user_id       uuid        NOT NULL REFERENCES accounts (user_id) ON DELETE CASCADE,
    law_id        text        NOT NULL,
    notify_before smallint    NOT NULL CHECK (notify_before IN (0, 1, 7, 30)),   -- 시행일 며칠 전에 알릴지
    created_at    timestamptz NOT NULL,
    PRIMARY KEY (user_id, law_id)
);
CREATE INDEX idx_law_subscriptions_law ON law_subscriptions (law_id);

-- 인앱 알림함(D74) — 배치 notify 가 만든다. 중복 키가 정확히 한 번을 보장(이 테이블이 Outbox 역할, ⑦c 이메일이 읽어 간다).
CREATE TABLE notifications (
    id             bigserial   PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES accounts (user_id) ON DELETE CASCADE,
    law_id         text        NOT NULL,
    effective_date date        NOT NULL,
    law_title      text        NOT NULL,
    notify_before  smallint    NOT NULL,
    created_at     timestamptz NOT NULL,
    read_at        timestamptz,
    UNIQUE (user_id, law_id, effective_date, notify_before)
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
