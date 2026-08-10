-- 문제의 실행 제한을 런타임(언어:버전)별로 덮어쓴다 (#97).
--
-- 행이 없으면 문제의 기본 제한을 그대로 쓴다. 즉 이 표는 "예외만" 담는다 —
-- 모든 문제 × 모든 런타임을 채우면 런타임이 늘 때마다 전부 손봐야 한다.
CREATE TABLE problem_runtime_limits (
    id              BIGSERIAL PRIMARY KEY,
    problem_id      BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    runtime_id      VARCHAR(40) NOT NULL,
    time_limit_ms   INT         NOT NULL CHECK (time_limit_ms BETWEEN 100 AND 30000),
    memory_limit_mb INT         NOT NULL CHECK (memory_limit_mb BETWEEN 16 AND 2048),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

-- 살아 있는 행에만 유니크를 건다. 소프트 삭제 후 같은 런타임을 다시 넣을 수 있어야 한다.
CREATE UNIQUE INDEX uq_problem_runtime_limit
    ON problem_runtime_limits (problem_id, runtime_id)
    WHERE deleted_at IS NULL;
