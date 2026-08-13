-- 이메일 인증 (#233).
--
-- 지금까지 가입할 때 이메일을 받지만 **그 주소가 진짜인지 확인하지 않았다.**
-- 비밀번호를 잃으면 계정을 잃는 것과 같고(#315), 소속 인증(#240)은 도메인 확인에
-- 기대는데 확인되지 않은 주소로는 아무 의미가 없다.

-- **기존 계정은 인증된 것으로 본다.** 전부 미인증으로 두면 인증 요구를 켜는 순간
-- 지금 쓰고 있는 사람들이 다 막힌다 — 그들이 잘못한 것이 없다.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
UPDATE users SET email_verified_at = created_at;

CREATE TABLE email_verifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- **토큰 자체를 저장하지 않는다.** 표를 읽을 수 있는 사람이 남의 계정을 인증할 수
    -- 있으면 안 된다 — 비밀번호를 해시로 두는 것과 같은 이유다.
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- 한 번만 쓰인다. 쓴 시각을 남겨 두면 "이미 인증했다" 와 "그런 토큰이 없다" 를
    -- 나눠 말할 수 있다.
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 토큰으로 찾는다. 유니크로 두어 우연한 충돌도 막는다.
CREATE UNIQUE INDEX idx_email_verifications_token ON email_verifications (token_hash);
-- 재발송 쿨다운과 하루 상한을 세는 질의가 이것을 탄다.
CREATE INDEX idx_email_verifications_user ON email_verifications (user_id, created_at DESC);
