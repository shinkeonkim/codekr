-- NoSQL 문제의 스펙 (#455).
--
-- **채점 모델이 SQL 과 다르다.** SQL 은 쿼리 하나를 던져 결과 집합을 받지만, 제출이
-- **명령의 연속**이면 남는 것은 결과가 아니라 **상태**다. 그래서 표의 모양도 다르다 —
-- `schema`/`answer` 자리에 `seed`/`answer`/`verify` 가 온다.
-- 유형 목록을 다시 적는다. `JUDGE_FUNCTION`(#421)은 이 브랜치에 없지만 목록에 남긴다 —
-- 두 갈래가 각자 이 제약을 다시 쓰므로, 어느 쪽이 나중에 들어와도 서로의 유형을 지우지
-- 않게 하려는 것이다.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_problem_kind_check;
ALTER TABLE problems ADD CONSTRAINT problems_problem_kind_check
    CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_NOSQL', 'JUDGE_FUNCTION', 'QUIZ', 'MANUAL'));

CREATE TABLE problem_nosql_specs (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    -- 시작 상태를 만드는 명령. 관리자로 넣는다. 문제가 소유한다.
    seed_commands   TEXT,
    -- 정답 명령의 연속. 기대 상태를 만든다.
    answer_commands TEXT        NOT NULL,
    -- **끝난 뒤의 상태를 읽는 명령.** SQL 과 달리 선택이 아니다 — 명령의 연속에는
    -- 견줄 "결과 집합" 이 없으므로, 이것이 없으면 무엇을 정답으로 볼지가 없다.
    verify_commands TEXT        NOT NULL,
    -- 줄 순서를 무시할지. **기본은 무시하지 않는다** — SQL 의 행 순서와 반대다.
    -- 정렬 집합·리스트에서 순서는 자료의 일부다.
    ignore_order    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
