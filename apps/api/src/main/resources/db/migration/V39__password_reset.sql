-- 비밀번호 재설정 (#315).
--
-- 지금 계정에 닿는 길은 이메일과 비밀번호뿐이다. 잊으면 푼 문제·점수·티어·뱃지가
-- **되돌릴 수 없이 사라진다.**

-- 비밀번호를 마지막으로 바꾼 시각.
--
-- **이 값보다 먼저 발급된 갱신 토큰은 통하지 않는다** (#315). 비밀번호를 바꾸는 흔한
-- 이유가 "남이 들어와 있는 것 같아서" 인데, 끊지 않으면 그 사람이 계속 들어와 있다.
-- 액세스 토큰은 Redis 표시로 즉시 끊고, 갱신 토큰은 이 값으로 끊는다.
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;

CREATE TABLE password_resets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 인증 토큰(#233)과 같은 이유로 해시만 둔다. 이쪽은 **로그인 수단 자체를 갈아
    -- 끼우므로** 새어 나갔을 때의 무게가 더 크다.
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_password_resets_token ON password_resets (token_hash);
CREATE INDEX idx_password_resets_user ON password_resets (user_id, created_at DESC);
