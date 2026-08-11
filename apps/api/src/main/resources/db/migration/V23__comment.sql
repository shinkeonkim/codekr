-- 댓글과 대댓글 (#138).
--
-- ## 깊이 제한을 저장에는 두지 않는다
--
-- "대댓글까지만" 으로 막으면 세 번째 발언부터는 누구에게 하는 말인지 사라진다.
-- **화면에서만 접는다** — 데이터에는 제한을 두지 않는다.
CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- 없으면 최상위 댓글이다.
    parent_id  BIGINT      REFERENCES comments (id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- **삭제해도 행을 지우지 않는다** (ADR-0007). 자식이 달린 댓글을 물리 삭제하면
-- 남의 글이 함께 사라진다. 화면에는 "삭제된 댓글" 로 남기고 자식은 그대로 보인다.

-- 한 번의 조회로 트리를 만들려면 글 단위로 전부 읽어야 한다. 그 축의 인덱스다.
CREATE INDEX idx_comments_post ON comments (post_id, id);
