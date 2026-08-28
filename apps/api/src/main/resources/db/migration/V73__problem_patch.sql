-- 읽고 고치는 문제 (#651).
--
-- **새 표가 없다.** 장치가 전부 이미 있기 때문이다 — 시작 코드는 `problem_files`(#457),
-- 숨긴 시험은 `problem_harnesses`(#421), 채점 대상은 `problem_testcases` 다.
-- 여기서 하는 일은 유형 이름을 하나 여는 것뿐이다.
--
-- **그런데도 유형을 나눈다.** `JUDGE_FUNCTION` 이 `JUDGE_STDIO` 에 얹히지 않은 이유와
-- 같다: "시작 코드가 망가져 있으면 고치는 문제" 라는 암묵 규칙을 만들면, 시작 코드를
-- 손볼 때 문제의 성격이 조용히 바뀐다.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_problem_kind_check;
ALTER TABLE problems ADD CONSTRAINT problems_problem_kind_check
    CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_REDIS', 'JUDGE_MONGODB',
                            'JUDGE_INTERACTIVE', 'JUDGE_FUNCTION', 'JUDGE_REGEX',
                            'JUDGE_GIT', 'JUDGE_PATCH', 'QUIZ', 'MANUAL'));
