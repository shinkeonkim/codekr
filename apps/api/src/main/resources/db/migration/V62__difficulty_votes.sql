-- 난이도 투표 (#477).
--
-- **사용자가 사이트 자체를 낫게 만드는 첫 통로다.** 지금까지 사용자가 만드는 것은
-- 제출·글·댓글뿐이었고 전부 자기 것이었다.
--
-- **사용자 × 문제 = 1표**이고 바꿀 수 있다. 처음 낸 표에 묶어 두면 문제가 고쳐졌을 때
-- (테스트케이스가 늘거나 지문이 명확해졌을 때) 옛 판단이 그대로 남는다.
CREATE TABLE problem_difficulty_votes (
    problem_id BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 난이도 단계 (1~30). `difficulty_level` 과 같은 눈금이다.
    level      INT         NOT NULL CHECK (level BETWEEN 1 AND 30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (problem_id, user_id)
);

-- 집계는 문제 단위로 읽는다.
CREATE INDEX idx_problem_difficulty_votes_problem ON problem_difficulty_votes (problem_id);
