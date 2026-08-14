-- SQL 문제는 어느 DB 인지 정한다 (#454).
--
-- SQL 런타임이 둘이 된 순간(PostgreSQL, MariaDB) "비워 두면 전부 허용"(#419)의 뜻이
-- SQL 문제에서 달라졌다 — PostgreSQL 문법으로 쓴 스키마와 정답 쿼리가 MariaDB 제출에서도
-- 돌게 된다. 그러면 **출제자의 스키마가 먼저 깨져** 제출자는 자기 잘못이 아닌
-- SYSTEM_ERROR 를 받는다.
--
-- 지금 있는 SQL 문제는 전부 PostgreSQL 로 쓰였다. 그것을 그대로 적어 둔다 —
-- 조용히 달라지는 것이 없어야 한다.
INSERT INTO problem_allowed_runtimes (problem_id, runtime_id)
SELECT p.id, 'sql:postgres16'
FROM problems p
WHERE p.problem_kind = 'JUDGE_SQL'
  AND NOT EXISTS (
      SELECT 1 FROM problem_allowed_runtimes a WHERE a.problem_id = p.id
  );
