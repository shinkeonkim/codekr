-- 일별 활동을 제출 테이블에서 분리한다 (#105).
--
-- submissions 는 가장 빨리 커지는 테이블이다. 활동 그래프를 열 때마다 사용자 단위로
-- 그것을 훑는 구조는 오래 못 간다. #81 에서 스트릭을 전체 기간 기준으로 바꾸면서
-- 부담이 더 커졌다 — 조회 범위와 무관하게 그 사용자의 활동 날짜 전체를 읽는다.
--
-- 여기는 **사용자 × 활동한 날 = 1행**이라 제출 수와 무관하게 작다.
CREATE TABLE user_daily_activity (
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 하루의 경계는 Asia/Seoul 로 확정해 저장한다 (docs/08). 읽는 쪽이 다시 변환하지 않는다.
    activity_date    DATE        NOT NULL,
    submission_count INT         NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, activity_date)
);

CREATE INDEX idx_user_daily_activity_date ON user_daily_activity (user_id, activity_date);

-- 기존 제출에서 채워 넣는다. 이 표가 비어 있으면 모든 사용자의 스트릭이 0 이 된다.
INSERT INTO user_daily_activity (user_id, activity_date, submission_count)
SELECT user_id,
       (created_at AT TIME ZONE 'Asia/Seoul')::date AS activity_date,
       count(*)
FROM submissions
WHERE deleted_at IS NULL AND kind = 'USER' AND status = 'COMPLETED'
GROUP BY user_id, activity_date;
