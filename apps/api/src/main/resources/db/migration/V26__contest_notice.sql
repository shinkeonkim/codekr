-- 대회 공지와 질의 (#147).
--
-- **게시판(#137)을 재사용하지 않는다.** 게시판 글은 누구나 읽지만, 대회 질의는
-- 참가자만 읽어야 하고 답변은 공개/비공개가 갈린다 — 권한 모양이 다르다.
-- 게시판에 대회 범위 조건을 얹으면 그 조건이 게시판 전체의 규칙이 된다.
CREATE TABLE contest_notices (
    id         BIGSERIAL PRIMARY KEY,
    contest_id BIGINT       NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    created_by BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_contest_notices ON contest_notices (contest_id, id DESC) WHERE deleted_at IS NULL;

-- 질의. 참가자가 묻고 운영자가 답한다.
CREATE TABLE contest_questions (
    id         BIGSERIAL PRIMARY KEY,
    contest_id BIGINT      NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    -- 어느 문제에 대한 질문인지. 없으면 대회 전체에 대한 질문이다.
    problem_id BIGINT      REFERENCES problems (id) ON DELETE SET NULL,
    asker_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    answer     TEXT,
    -- **공개 답변은 전원이 본다.** 한 사람에게만 준 정보가 유리하게 작용하면 안 되는
    -- 질문이 있고, 반대로 그 사람만의 사정인 질문도 있다. 운영자가 고른다.
    answer_public BOOLEAN  NOT NULL DEFAULT false,
    answered_by   BIGINT   REFERENCES users (id) ON DELETE SET NULL,
    answered_at   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contest_questions ON contest_questions (contest_id, id DESC);
CREATE INDEX idx_contest_questions_asker ON contest_questions (contest_id, asker_id);
