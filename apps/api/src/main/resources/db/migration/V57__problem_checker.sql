-- 스페셜 저지 (#452).
--
-- **정답이 하나뿐인 문제만 낼 수 있었다.** 채점이 출력을 기대값과 견주는 것이라,
-- 조건을 만족하는 아무 배치·임의 순서·"성질이 맞는가" 를 묻는 문제를 낼 수 없었다.
--
-- 견주는 대신 **물어본다** — 출제자가 쓴 코드가 판정한다.
--
-- 문제에 붙는 값이 하나이므로 별도 표를 두지 않는다. 언어도 파이썬 하나로 고정이라
-- 담을 것이 소스 하나뿐이다.
ALTER TABLE problems ADD COLUMN checker_source TEXT;

-- 출력 비교 방식에 CHECKER 를 더한다 (#279 가 만든 자리).
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_output_comparison_check;
ALTER TABLE problems
    ADD CONSTRAINT problems_output_comparison_check
        CHECK (output_comparison IN ('EXACT', 'FLOAT', 'CHECKER'));

COMMENT ON COLUMN problems.checker_source IS
    '#452 — 스페셜 저지의 채점 코드(파이썬). 사용자에게 절대 내려가지 않는다';
