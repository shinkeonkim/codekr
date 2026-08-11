-- 문제별 질문 (#139).
--
-- **게시글을 새로 만들지 않고 붙인다.** 질문은 게시글이고, 답변은 댓글이다 —
-- 같은 것을 두 벌 만들면 렌더링·권한·삭제 규칙이 둘로 갈라진다.
--
-- 커뮤니티 목록에서도 함께 보인다. 분리하면 질문이 두 곳에 흩어지고,
-- "다음 사람이 먼저 읽고 간다" 는 목적이 약해진다.
ALTER TABLE posts ADD COLUMN problem_id BIGINT REFERENCES problems (id) ON DELETE SET NULL;

CREATE INDEX idx_posts_problem ON posts (problem_id, id DESC) WHERE deleted_at IS NULL;
