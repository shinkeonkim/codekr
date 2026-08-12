-- 문제별 풀이 통계를 저장한다 (#205).
--
-- 지금까지는 조회 시점에 셌다 (#84). 한 페이지분 20개를 세는 것은 쌌지만,
-- **정답률로 정렬하려면 모든 문제의 집계가 필요하다** — 그것은 다른 일이다.
--
-- **원자료는 여전히 submissions 하나다.** 이 표는 캐시이고, 언제든 다시 셀 수 있다
-- (`ProblemStatsSyncRepository.refreshAll`). 그래서 어긋나도 되돌릴 길이 있다 —
-- 저장만 하고 재계산 경로를 만들지 않으면 어긋난 값이 영구히 남는다.
CREATE TABLE problem_stats (
    problem_id BIGINT PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    -- 제출한 사람 수. 제출 건수가 아니다 — 같은 사람이 스무 번 내도 하나다.
    submitters INT         NOT NULL DEFAULT 0,
    solvers    INT         NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 정답률은 저장하지 않고 **계산해 둔다**. 두 값에서 나오는 것이라 따로 두면 어긋난다.
    --
    -- 제출자가 없으면 NULL 이다. **0/0 은 정답률이 없는 것이지 0% 가 아니다** —
    -- 0 으로 두면 정렬에서 새 문제가 맨 앞이나 맨 뒤를 통째로 차지한다.
    acceptance NUMERIC GENERATED ALWAYS AS (
        CASE WHEN submitters = 0 THEN NULL ELSE solvers::numeric / submitters END
    ) STORED
);

-- 정렬용. 양방향 정렬이라 인덱스 하나로 둘 다 탄다.
CREATE INDEX idx_problem_stats_solvers ON problem_stats (solvers);
CREATE INDEX idx_problem_stats_acceptance ON problem_stats (acceptance);
