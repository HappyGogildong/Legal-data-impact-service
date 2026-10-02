-- 알림 이메일 수신 동의(D61). email 은 동의가 있을 때만 존재한다 — 로그인만으로는 저장하지 않는다(D60 정합).
ALTER TABLE accounts ADD COLUMN email_consented_at timestamptz;

-- 슬라이스 ①은 로그인 시 동의 없이 email 을 저장했다 → 동의 근거가 없으므로 비운다
UPDATE accounts SET email = NULL;

-- email 과 동의 일시는 항상 함께 있거나 함께 null
ALTER TABLE accounts ADD CONSTRAINT accounts_email_requires_consent
    CHECK ((email IS NULL) = (email_consented_at IS NULL));
