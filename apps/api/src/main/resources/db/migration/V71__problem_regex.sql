-- 정규식 문제 (#653).
--
-- **유형을 새로 만든다.** 기획서(#653 의 근거)는 `JUDGE_STDIO` 로 낼 수 있다고 적었는데,
-- 하네스 구조를 확인해 보니 그렇지 않았다:
--
--   * `JUDGE_STDIO` 로 두면 사용자가 **프로그램을 쓰게 되어** 정규식 문제가 아니게 된다
--   * 함수형 하네스(#421)에 얹으면 사용자에게 **언어 선택이 새어 나온다** —
--     정규식 문제에서 파이썬이냐 자바냐를 고르게 할 이유가 없다
--
-- 모양은 SQL·Redis·MongoDB 와 같다: 하나를 제출하고, **문제가 정한** 런타임에서
-- 하네스가 돌리고, 기대와 실제를 견준다.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_problem_kind_check;
ALTER TABLE problems ADD CONSTRAINT problems_problem_kind_check
    CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_REDIS', 'JUDGE_MONGODB',
                            'JUDGE_INTERACTIVE', 'JUDGE_FUNCTION', 'JUDGE_REGEX',
                            'QUIZ', 'MANUAL'));

CREATE TABLE problem_regex_specs (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    /*
        확인할 문자열. 한 줄에 하나이고 첫 글자가 판정이다.

            +abc123     맞아야 한다
            -abc        맞으면 안 된다

        **정답 패턴을 두지 않는다.** SQL·Redis 는 정답을 돌려 기대값을 만들지만
        여기서는 이 판정이 곧 기대값이다 — 정답 패턴으로 만들면 **출제자가 실수한
        패턴이 그대로 정답이 되어** 아무도 그것을 잡을 수 없다.
    */
    cases       TEXT    NOT NULL,
    -- 전체가 맞아야 하는가. **문제가 정하고 지문에 적어야 한다** —
    -- `match` 와 `search` 는 다른 문제다.
    full_match  BOOLEAN NOT NULL DEFAULT true,
    ignore_case BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
