-- 랭킹 점수 (#57, #85).
--
-- **한 문제는 최초 정답 1회만 점수를 준다.** 같은 문제를 다시 풀어도 점수가 늘지 않는다.
-- 그래서 (user_id, problem_id) 가 기본키다 — 중복이 구조적으로 불가능하다.
CREATE TABLE user_problem_scores (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_id BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    -- 맞힌 시점의 난이도로 매긴 점수. 난이도가 바뀌면 재계산으로 갱신한다.
    score      INT         NOT NULL,
    solved_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, problem_id)
);

-- 랭킹은 사용자별로 상위 N개를 골라 합산한다 (#85). 그 정렬에 쓰는 인덱스.
CREATE INDEX idx_user_problem_scores_rank ON user_problem_scores (user_id, score DESC);

-- 동점 처리에 쓰는 '최초 해결 시각'.
CREATE INDEX idx_user_problem_scores_solved_at ON user_problem_scores (user_id, solved_at);
