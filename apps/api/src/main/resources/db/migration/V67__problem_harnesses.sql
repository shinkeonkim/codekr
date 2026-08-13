-- 함수만 구현하는 문제의 하네스 (#421).
--
-- **문제 × 언어 = 하네스 하나.** 언어마다 부르는 방법이 다르므로(파이썬은 import,
-- 자바는 클래스) 하나로 둘 수 없다.
--
-- **하네스를 쓴 언어가 곧 허용 목록이다** (#419). 새 규칙을 만들지 않는다 — 두 곳이
-- 같은 것을 정하면 어긋난다.
CREATE TABLE problem_harnesses (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    runtime_id VARCHAR(40) NOT NULL,
    -- 사용자 코드를 부르는 **보이지 않는 코드**. 사용자에게 절대 내려가지 않는다.
    source     TEXT        NOT NULL,
    -- 사용자가 채울 껍데기. 화면의 시작 코드가 된다 — "빈 화면" 이 아니라 "채울 자리" 다.
    template   TEXT        NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_problem_harnesses ON problem_harnesses (problem_id, runtime_id);
