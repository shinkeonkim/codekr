-- 제출 코드 열람 기록 (#136).
--
-- ## 기록하는 것과 하지 않는 것
--
-- **소스 코드가 실제로 내려간 조회만** 센다. 판정만 보는 것은 목록에서도 보이고,
-- 무게가 다른 둘을 한 숫자로 합치면 그 숫자가 뜻을 잃는다.
--
-- 자기 제출과 어드민 조회는 세지 않는다 — 운영 행위가 알림이 되면 안 된다.
--
-- ## 하루 한 행
--
-- (제출, 조회자, 날짜)가 기본키다. 같은 사람이 같은 날 몇 번을 봐도 한 행이라,
-- 새로고침이나 뒤로가기가 숫자를 부풀리지 않고 쓰기도 폭주하지 않는다.
CREATE TABLE submission_views (
    submission_id BIGINT      NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    viewer_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewed_on     DATE        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (submission_id, viewer_id, viewed_on)
);

-- 작성자별 일일 집계가 이 축으로 읽는다.
CREATE INDEX idx_submission_views_day ON submission_views (viewed_on);

-- 열람 알림을 받을지. **기본은 끔이다.**
--
-- 열람 기록은 조회자의 프라이버시를 건드린다. 켜져 있는 것이 기본이면 "동의 없이
-- 추적" 이 기본이 된다. 그리고 꺼져 있으면 **아예 기록하지 않으므로** 쓰기 부담도 없다.
ALTER TABLE users ADD COLUMN view_notification_enabled BOOLEAN NOT NULL DEFAULT false;
