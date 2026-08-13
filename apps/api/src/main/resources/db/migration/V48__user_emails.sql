-- 확인한 메일 주소를 여러 개 갖는다 (#396, #240 1단계).
--
-- 소속 인증이 이것에 통째로 기댄다 — 학교·회사 메일을 확인해야 소속이 붙는데,
-- **로그인 주소를 학교 메일로 바꾸게 하면 졸업하는 순간 로그인을 잃는다.**
CREATE TABLE user_emails (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    email       VARCHAR(255) NOT NULL,
    -- 확인된 것만 이 표에 들어온다. 확인 전에는 토큰만 있고 행이 없다.
    verified_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- **같은 주소를 두 사람이 확인할 수 없다.** 그러지 않으면 한 학교 메일로 두 계정이
-- 같은 소속을 얻는다.
CREATE UNIQUE INDEX uq_user_emails_email ON user_emails (email);
CREATE INDEX idx_user_emails_user ON user_emails (user_id);

-- 어느 주소를 확인하려는 토큰인지 (#396).
--
-- **비우면 로그인 주소다** — 가입 때 보내는 그것이다. 값이 있으면 추가 주소를
-- 확인하는 토큰이고, 확인되면 user_emails 에 행이 생긴다.
ALTER TABLE email_verifications ADD COLUMN email VARCHAR(255);
