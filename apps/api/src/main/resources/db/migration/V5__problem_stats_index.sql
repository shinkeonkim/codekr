-- 문제별 "제출한 사람 수 / 맞은 사람 수" 집계를 위한 인덱스 (#84).
--
-- 집계를 따로 저장하지 않고 조회 시점에 센다. 저장하면 재채점·제출 삭제 때 어긋나고,
-- 어긋난 것을 되돌릴 방법을 또 만들어야 한다. 지금 규모에서는 세는 편이 싸고 항상 맞다.
-- 느려지면 그때 캐시를 얹는다 (docs/02 참고).
CREATE INDEX idx_submissions_problem_user
    ON submissions (problem_id, user_id)
    WHERE deleted_at IS NULL;

-- 맞은 사람만 셀 때 쓰는 부분 인덱스.
CREATE INDEX idx_submissions_problem_solver
    ON submissions (problem_id, user_id)
    WHERE deleted_at IS NULL AND verdict = 'ACCEPTED';
