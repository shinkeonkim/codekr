-- 문제마다 풀 수 있는 언어 (#419).
--
-- 전에는 `problem_kind` 하나로만 갈렸다 — 그것은 **문제의 종류**(stdio·SQL·퀴즈)이지
-- 언어 목록이 아니다. 그래서 모든 stdio 문제가 stdio 런타임 전부를 똑같이 내놓았고,
-- "파이썬으로만 푸는 문제" 를 낼 수 없었다. 아희·엄랭(#394)이 들어온 뒤로는 반대 문제도
-- 생겼다 — 모든 문제 목록에 그것이 뜬다.
--
-- **`problem_runtime_limits`(#97) 와 합치지 않는다.** 그쪽은 "이 런타임은 제한이 다르다"
-- 이지 "이 런타임만 허용한다" 가 아니다. 합치면 제한을 적어 둔 런타임만 허용되는 것으로
-- 바뀌어 **기존 문제의 동작이 조용히 달라진다.**
CREATE TABLE problem_allowed_runtimes (
    id         BIGSERIAL PRIMARY KEY,
    problem_id BIGINT      NOT NULL REFERENCES problems (id),
    runtime_id VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- **행이 하나도 없으면 전부 허용이다.** 기존 문제가 그대로 돌아야 한다.
CREATE UNIQUE INDEX uq_problem_allowed_runtimes ON problem_allowed_runtimes (problem_id, runtime_id);
