-- 문제 태그 (#232).
--
-- 카테고리(problems.category)와 다른 축이다. 카테고리는 "무엇에 대한 문제인가"(SQL·OS·네트워크),
-- 태그는 "어떤 기법으로 푸는가"(DP·이분 탐색·최단 경로)다. 알고리즘 문제 전부가 카테고리
-- ALGORITHM 하나에 들어가기 때문에, 카테고리로는 "DP 만 골라 풀기" 가 되지 않는다.
--
-- **평평하게 둔다.** "그래프 > 최단 경로 > 다익스트라" 같은 계층은 필터를 곱절로 복잡하게
-- 만드는데(상위를 고르면 하위까지 포함할 것인가), 지금 필요한 것은 그 복잡도가 아니다.
-- 계층이 필요해지면 parent_id 를 더하는 편이 이 표를 쪼개는 것보다 싸다.
CREATE TABLE tags (
    id          BIGSERIAL PRIMARY KEY,
    -- URL 과 필터 파라미터에 쓰는 이름. 화면에 보이는 이름(name)이 바뀌어도 링크가 살아남는다.
    slug        VARCHAR(60)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    -- 무엇을 뜻하는 태그인지. 비슷한 태그가 둘 생기는 것을 막는 유일한 수단이다.
    description VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_tags_slug ON tags (slug);
CREATE UNIQUE INDEX ux_tags_name ON tags (name);

CREATE TABLE problem_tags (
    problem_id BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    tag_id     BIGINT      NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (problem_id, tag_id)
);

-- "이 태그가 붙은 문제" 를 세고 거르는 것이 이 표의 주된 쓰임이다.
-- 기본 키가 (problem_id, tag_id) 라 그 방향은 이미 인덱스가 있고, 반대 방향만 더한다.
CREATE INDEX ix_problem_tags_tag ON problem_tags (tag_id);
