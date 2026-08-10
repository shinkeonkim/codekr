-- 활동 그래프의 칸에 "그날 맞힌 문제 수" 를 더한다 (#133).
--
-- 전에는 제출 수만 있어서, 진한 칸이 **한 문제를 20번 틀린 날**인지
-- **20문제를 푼 날**인지 구분되지 않았다.
--
-- ## 정의: 그날 정답 판정을 받은 **서로 다른 문제 수**
--
-- "그날 처음 맞힌 문제" 로 하면 "새로 푼 문제" 에 더 가깝지만, 그 값은 **과거에 의존한다.**
-- 재채점(#107)으로 옛 판정이 뒤집히면 그 뒤 모든 날의 숫자가 달라지고, 하루치만 다시
-- 세는 갱신 방식(#105)으로는 그것을 따라갈 수 없다.
--
-- 대가: 어제 푼 문제를 오늘 다시 맞히면 오늘도 세어진다. 그래서 화면에서는
-- "새로 푼" 이 아니라 **"맞힌 문제"** 라고 부른다.
ALTER TABLE user_daily_activity ADD COLUMN solved_count INT NOT NULL DEFAULT 0;

-- 기존 데이터를 채운다. 0 으로 두면 과거의 모든 칸이 "아무것도 못 푼 날" 로 보인다.
UPDATE user_daily_activity a
SET solved_count = counted.solved
FROM (
    SELECT user_id,
           (created_at AT TIME ZONE 'Asia/Seoul')::date AS activity_date,
           count(DISTINCT problem_id)                   AS solved
    FROM submissions
    WHERE deleted_at IS NULL
      AND kind = 'USER'
      AND verdict = 'ACCEPTED'
    GROUP BY 1, 2
) counted
WHERE a.user_id = counted.user_id AND a.activity_date = counted.activity_date;
