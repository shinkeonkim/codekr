-- MongoDB 문제의 스펙 (#527).
--
-- **Redis(#455)와 모양이 같고 담기는 것이 다르다.** 시드 → 정답 → 확인이라는 뼈대는
-- 같지만, 여기는 `mongosh` 스크립트이고 저기는 redis 명령이다. 한 표에 담으면
-- "이 칸은 어느 제품의 것인가" 를 행마다 따져야 한다.
--
-- **유형을 나눈 이유**: 질의 언어가 아예 다르다. #454 가 SQL 에 MariaDB 를 더할 때는
-- 런타임만 얹으면 됐지만(같은 언어) 여기는 그 방법이 통하지 않는다.
--
-- 이름을 `JUDGE_MONGODB` 로 좁게 둔다. #455 가 "NoSQL" 이라는 넓은 이름 때문에
-- 낼 수 없는 문제를 낼 수 있다고 읽히던 것을 겪었다.
ALTER TABLE problems DROP CONSTRAINT IF EXISTS problems_problem_kind_check;
ALTER TABLE problems ADD CONSTRAINT problems_problem_kind_check
    CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_REDIS', 'JUDGE_MONGODB',
                            'JUDGE_INTERACTIVE', 'JUDGE_FUNCTION', 'QUIZ', 'MANUAL'));

CREATE TABLE problem_mongo_specs (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    -- 시작 상태를 만드는 스크립트. 관리자로 넣는다. 문제가 소유한다.
    seed_script   TEXT,
    -- 정답 스크립트. 기대 상태를 만든다.
    answer_script TEXT        NOT NULL,
    -- **끝난 뒤를 읽는 스크립트.** 선택이 아니다 — 이것이 없으면 무엇을 정답으로
    -- 볼지가 없다. `find` 를 찍으면 결과 집합이 되고 컬렉션을 세면 상태가 된다.
    verify_script TEXT        NOT NULL,
    -- 줄 순서를 무시할지. 기본은 무시하지 않는다 — Redis 와 같은 판단이다.
    ignore_order  BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
