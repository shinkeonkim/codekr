-- 부분 점수 묶음 (#473).
--
-- **99개를 맞혀도 지금은 그냥 틀린 답이다.** 배우는 사람은 어디까지 왔는지 모른 채
-- 다시 낸다. 묶음은 대개 제약 조건이다 — "N ≤ 1,000", "N ≤ 100,000", "만점".
--
-- **묶음 안을 다 맞혀야 그 점수를 받는다** (IOI 관례). 케이스마다 점수를 주면 묶음의
-- 뜻이 없어진다.
--
-- **랭킹에는 반영하지 않는다.** 만점만 "풀었다" 로 본다 — 부분 점수의 교육적 값은
-- 화면에 보이는 것으로 대부분 얻어지고, 랭킹까지 열면 #57·#58·#84·#105 가 전부
-- 흔들린다. 값은 남겨 두므로 나중에 열 수 있다.
CREATE TABLE problem_testcase_groups (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    group_no   INT         NOT NULL,
    -- 이 묶음을 다 맞혔을 때 받는 점수.
    score      INT         NOT NULL CHECK (score >= 0),
    -- 화면에 보이는 이름. "N ≤ 1,000" 처럼 제약을 그대로 적는 것이 힌트가 된다.
    label      VARCHAR(60) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_problem_testcase_groups ON problem_testcase_groups (problem_id, group_no);

-- 묶음이 없는 기존 문제는 그대로다 — NULL 이면 부분 점수가 없는 문제다.
ALTER TABLE problem_testcases ADD COLUMN group_no INT;

-- 제출이 받은 점수 (#473). 묶음이 없으면 NULL 이다.
ALTER TABLE submissions ADD COLUMN score INT;
ALTER TABLE submissions ADD COLUMN max_score INT;
