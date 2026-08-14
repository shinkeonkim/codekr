-- 상태를 바꾸는 SQL 문제 (#453).
--
-- `SELECT` 문제는 **결과 집합**을 견주면 됐다. `INSERT`·`UPDATE`·`CREATE TABLE` 은
-- 결과 집합이 없다 — 바뀌는 것은 **DB 의 상태**다. 그래서 두 가지가 필요하다:
-- 끝난 뒤의 상태를 읽는 쿼리와, 제출에게 쓰기를 열어 줄지의 결정.
ALTER TABLE problem_sql_specs
    -- 끝난 뒤의 상태를 읽는 쿼리. 비어 있으면 지금까지처럼 제출 쿼리의 결과를 견준다.
    -- 정답 스크립트를 돌린 DB 와 제출을 돌린 DB 에서 각각 돌려 그 결과를 비교한다.
    ADD COLUMN verify_sql TEXT,
    -- **쓰기는 문자열 필터가 아니라 권한으로 연다.** 필터는 우회되지만 권한은 아니다.
    -- 기본이 false 인 이유: 지금 있는 SELECT 문제가 조용히 쓰기 가능해지면 안 된다.
    ADD COLUMN allow_write BOOLEAN NOT NULL DEFAULT false;

-- 상태를 견주려면 읽는 쿼리가 있어야 한다. 하나만 켜 두면 채점이 조용히 예전 방식으로
-- 돌아가 출제자는 자기 문제가 무엇을 재는지 모르게 된다.
ALTER TABLE problem_sql_specs
    ADD CONSTRAINT problem_sql_specs_write_needs_verify
        CHECK (allow_write = false OR verify_sql IS NOT NULL);
