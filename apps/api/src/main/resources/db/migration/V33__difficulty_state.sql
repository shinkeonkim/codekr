-- 미평가(UNRATED)·평가안함(NO_RATE) 을 표현한다 (#195).
--
-- **보초값(0·-1)을 쓰지 않는다.** 그러면 정렬과 범위 검색이 조용히 깨진다 — 틀린 결과가
-- 나오는 것이 아니라 *그럴듯한* 결과가 나온다. `-1` 은 브론즈보다 쉬운 문제처럼 정렬되고,
-- 티어 필터의 `BETWEEN` 에는 안 잡히면서 "전체" 에는 잡힌다.
--
-- 대신 레벨을 **없앨 수 있게** 하고, 상태를 따로 적는다.
ALTER TABLE problems
    ADD COLUMN difficulty_state VARCHAR(16) NOT NULL DEFAULT 'RATED';

ALTER TABLE problems ALTER COLUMN difficulty_level DROP NOT NULL;

-- 기존 문제는 전부 RATED 다 (기본값). 값은 그대로 둔다.
--
-- 상태와 레벨이 어긋나지 않게 못 박는다. 이것이 없으면 "RATED 인데 레벨이 없는" 행이
-- 생기고, 그때 점수 계산이 조용히 0 이 된다.
ALTER TABLE problems
    ADD CONSTRAINT problems_difficulty_state_check
        CHECK (difficulty_state IN ('RATED', 'UNRATED', 'NO_RATE')),
    ADD CONSTRAINT problems_difficulty_level_matches_state
        CHECK ((difficulty_state = 'RATED') = (difficulty_level IS NOT NULL));

-- 목록 필터 인덱스를 다시 만든다. 상태가 먼저다 — "미평가만" 을 고르는 것이
-- 티어 범위 검색보다 좁은 조건이고, RATED 안에서만 레벨 범위가 뜻이 있다.
DROP INDEX IF EXISTS idx_problems_filter;
CREATE INDEX idx_problems_filter ON problems (category, difficulty_state, difficulty_level);
