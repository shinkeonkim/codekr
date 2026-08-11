-- 문제 유형 (#59).
--
-- **사용자에게 보이는 변화가 없는 준비 작업이다.** 지금 있는 문제는 전부 stdin/stdout
-- 채점이고, 기본값이 그것이라 기존 문제의 동작은 그대로다.
--
-- 유형별 데이터는 JSON 한 컬럼에 몰지 않고 테이블로 나눈다 (기획서 §3). JSON 은 시작이
-- 빠르지만 어드민 검증·마이그레이션·조회가 모두 애플리케이션 몫이 된다.
-- SQL 유형의 problem_sql_spec 은 #60 에서 만든다.
ALTER TABLE problems
    ADD COLUMN problem_kind VARCHAR(20) NOT NULL DEFAULT 'JUDGE_STDIO'
        CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'QUIZ', 'MANUAL'));

-- 목록·검색이 유형으로 걸러질 것이므로 지금 인덱스를 둔다.
CREATE INDEX idx_problems_kind ON problems (problem_kind) WHERE deleted_at IS NULL;
