-- 회원 정지 (#224).
--
-- 지금까지 회원에게 할 수 있는 일은 "그대로 두기" 와 "되돌릴 수 없이 지우기"(#140)
-- 둘뿐이었다. 댓글 스팸 하나에 계정을 영구히 지우는 것은 과하다.
CREATE TABLE user_suspensions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 무엇을 막는지. WRITE(쓰기) / SUBMIT(제출) / ALL(둘 다).
    -- **읽기는 어떤 값으로도 막지 않는다** — 로그아웃하면 그대로 보이므로 막는 시늉이다.
    scope       VARCHAR(16) NOT NULL,
    reason      TEXT        NOT NULL,
    -- NULL 이면 기한 없음. 기한이 지나면 **저절로 풀린다** — 푸는 것을 사람이 기억해야
    -- 하면 영구 정지와 같아지므로, 상태를 뒤집는 배치를 두지 않고 조회에서 판단한다.
    ends_at     TIMESTAMPTZ,
    -- 어드민이 미리 푼 시각. 이 값이 있으면 기한과 무관하게 끝난 정지다.
    lifted_at   TIMESTAMPTZ,
    lifted_by   BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_by  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 요청마다 "이 사람이 지금 정지 중인가" 를 묻는다. 그 질의가 이 인덱스를 탄다.
CREATE INDEX idx_user_suspensions_active ON user_suspensions (user_id, id DESC)
    WHERE lifted_at IS NULL;

-- 목록은 최근 순으로 본다.
CREATE INDEX idx_user_suspensions_created ON user_suspensions (created_at DESC);
