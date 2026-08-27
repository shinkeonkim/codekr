-- 테스트를 쓰는 문제 (#652).
--
-- **채점이 뒤집혀 있다.** 다른 유형은 우리가 시험을 숨기고 사용자가 구현을 내지만,
-- 여기서는 사용자가 시험을 내고 우리가 구현을 숨긴다.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_problem_kind_check;
ALTER TABLE problems ADD CONSTRAINT problems_problem_kind_check
    CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_REDIS', 'JUDGE_MONGODB',
                            'JUDGE_INTERACTIVE', 'JUDGE_FUNCTION', 'JUDGE_REGEX',
                            'JUDGE_GIT', 'JUDGE_PATCH', 'JUDGE_MUTATION', 'QUIZ', 'MANUAL'));

CREATE TABLE problem_mutation_specs (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    -- 올바른 구현. 사용자의 시험이 이것은 **통과시켜야** 한다.
    -- 통과시키지 못하면 시험이 틀린 것이다.
    reference_source TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

/*
    버그를 심은 구현들. 사용자의 시험이 **전부 실패시켜야** 한다.

    **표를 나눈다.** 하나의 텍스트 칸에 구분자로 몰면 구현 안에 그 구분자가 들어갈 수
    있고, 그때 조용히 두 개로 쪼개진다 — 그러면 판정 수가 달라지고 아무도 모른다.

    `label` 은 출제자를 위한 것이다(무엇을 심었는지). **사용자에게 나가지 않는다** —
    나가면 무엇을 확인해야 할지가 곧 답이 된다.
*/
CREATE TABLE problem_mutants (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    seq        INT    NOT NULL,
    label      VARCHAR(200),
    source     TEXT   NOT NULL,
    UNIQUE (problem_id, seq)
);
