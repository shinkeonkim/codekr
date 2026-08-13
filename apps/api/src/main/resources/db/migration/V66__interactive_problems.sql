-- 인터랙티브 문제 (#474).
--
-- **도는 중에 주고받는다.** 스페셜 저지(#452)는 끝난 뒤 출력을 받아 판정하지만, 여기서는
-- 채점 코드와 제출이 동시에 돌며 서로에게 쓴다 — 이 유형이 묻는 것은 답을 아는 것이
-- 아니라 **답에 이르는 길을 설계하는 것**이다.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_problem_kind_check;
ALTER TABLE problems ADD CONSTRAINT problems_problem_kind_check
    CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_NOSQL', 'JUDGE_INTERACTIVE',
                            'JUDGE_FUNCTION', 'QUIZ', 'MANUAL'));

-- 대화를 주관하는 출제자의 코드. **사용자에게 절대 내려가지 않는다** — 정답의 일부다
-- (#452 의 채점 코드와 같은 규칙).
ALTER TABLE problems ADD COLUMN interactor_source TEXT;
