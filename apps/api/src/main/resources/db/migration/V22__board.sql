-- 커뮤니티 게시판 (#137).
--
-- ## 게시판을 셋으로 시작한다
--
-- **나중에 늘리기는 쉽지만 합치기는 어렵다.** 글이 쌓인 뒤에 게시판을 합치면 그 글들이
-- 어디로 가야 하는지 사람이 하나씩 정해야 한다. 그래서 적게 시작한다.
--
-- 태그로 가르는 방식도 검토했지만, 태그는 **정리하는 사람이 있어야** 유지된다.
-- 지금은 없다.
CREATE TABLE posts (
    id         BIGSERIAL PRIMARY KEY,
    board      VARCHAR(20)  NOT NULL CHECK (board IN ('FREE', 'QUESTION', 'NOTICE')),
    author_id  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    -- 마크다운 원문을 그대로 둔다. 렌더링은 화면이 한다 — 저장 시점에 HTML 로 바꾸면
    -- 렌더링 규칙을 고칠 때 이미 쌓인 글을 전부 다시 만들어야 한다.
    body       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_posts_board ON posts (board, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_author ON posts (author_id, id DESC) WHERE deleted_at IS NULL;

-- 제목·본문 검색. 지금 규모에서는 이것으로 충분하다.
CREATE INDEX idx_posts_title ON posts USING gin (to_tsvector('simple', title)) WHERE deleted_at IS NULL;
