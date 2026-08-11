-- 대회 제출 (#62).
--
-- 제출이 어느 대회의 것인지 표시한다. 없으면 평소 제출이다.
-- **대회 제출은 별도 큐로 간다** — 같은 큐를 쓰면 대회 제출이 평소 사용자의 채점을
-- 몇 분씩 밀어낸다.
ALTER TABLE submissions ADD COLUMN contest_id BIGINT REFERENCES contests (id) ON DELETE SET NULL;

-- 참가자·문제별 제출 빈도 제한과 순위 계산이 모두 이 축으로 읽는다.
CREATE INDEX idx_submissions_contest ON submissions (contest_id, user_id, problem_id, created_at DESC)
    WHERE contest_id IS NOT NULL AND deleted_at IS NULL;
