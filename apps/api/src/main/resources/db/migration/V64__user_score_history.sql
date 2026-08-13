-- 점수 변화 기록 (#476).
--
-- **다시 계산하지 않고 기록한다.** `user_problem_scores.solved_at` 으로 "그날까지의 합"
-- 을 계산할 수는 있지만, 그것은 **지금 난이도 기준**이다 — #194 가 드러낸 대로 난이도는
-- 바뀌고, 바뀔 때마다 과거 그래프가 흔들리면 그래프를 믿을 수 없다.
--
-- `users.peak_score` 를 따로 든 판단(#58)과 같은 결이다: **그때의 값이 중요하다.**
--
-- **하루 한 점이다.** 문제를 풀 때마다 남기면 표가 빠르게 커지는데, 그래프로 읽을 때
-- 하루보다 잘게 필요한 적이 없다. `user_daily_activity`(#105)도 하루 단위다.
CREATE TABLE user_score_history (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 그 날짜(KST 기준)의 **마지막** 점수. 같은 날 여러 번 오르면 덮어쓴다.
    on_date DATE   NOT NULL,
    score   INT    NOT NULL,
    -- 그때의 실력 티어 (#58). 티어가 바뀐 지점이 그래프의 이정표가 된다.
    tier_level INT NOT NULL,
    PRIMARY KEY (user_id, on_date)
);
