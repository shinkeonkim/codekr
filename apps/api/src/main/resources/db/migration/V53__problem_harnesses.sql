-- 함수 구현 유형을 허용 목록에 더한다 (#446, #421).
--
-- `problem_kind` 는 값 목록을 CHECK 로 못 박아 두었다 (V14). **그 목록이 곧 계약이라**
-- 새 유형을 코드에만 더하면 저장하는 순간 제약에 걸린다.
ALTER TABLE problems DROP CONSTRAINT problems_problem_kind_check;
ALTER TABLE problems
    ADD CONSTRAINT problems_problem_kind_check
        CHECK (problem_kind IN ('JUDGE_STDIO', 'JUDGE_SQL', 'JUDGE_FUNCTION', 'QUIZ', 'MANUAL'));

-- 문제의 언어별 하네스 (#446, #421).
--
-- **하네스는 출제자가 쓴 코드다.** 입력을 읽고 사용자가 구현한 함수를 부르고 결과를
-- 찍는다 — 사용자에게는 함수 껍데기만 보인다.
--
-- **`problem_templates`(#12) 와 다른 표다.** 그쪽은 사용자에게 보여 주는 시작 코드이고,
-- 이쪽은 **절대 보이면 안 되는 것**이다. 한 표에 담으면 "보여 주는 것" 과 "감추는 것"
-- 이 한 필드 차이가 되고, 그 구분이 코드 곳곳에 흩어진다.
CREATE TABLE problem_harnesses (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT      NOT NULL REFERENCES problems (id),
    runtime_id VARCHAR(40) NOT NULL,
    -- 하네스 소스. 실행기가 런타임이 정한 이름으로 저장한다 (#445).
    source     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- **문제 × 언어 = 하네스 하나.** 둘이면 어느 것으로 도는지 아무도 모른다.
CREATE UNIQUE INDEX uq_problem_harnesses ON problem_harnesses (problem_id, runtime_id);
