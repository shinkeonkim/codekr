-- 웹 내 알림 (#106).
--
-- 이메일·푸시는 하지 않는다. 외부 발송은 전달 실패·수신 거부·스팸 처리 같은 다른 문제를
-- 통째로 들여오고, 지금 알릴 것(재채점·대회 공지)은 사이트에 들어와야 의미가 있다.
CREATE TABLE notifications (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category   VARCHAR(20) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    -- 누르면 갈 곳. 같은 출처의 경로만 담는다 (외부 주소를 담지 않는다).
    link       VARCHAR(500),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

-- 미읽음 개수는 헤더에서 자주 읽는다. 부분 인덱스로 읽은 것을 아예 훑지 않게 한다.
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- 수신을 끈 카테고리만 담는다. 행이 없으면 받는다는 뜻이다.
--
-- 전체 조합을 저장하지 않는 이유: 카테고리가 늘 때마다 모든 사용자 행을 채워 넣어야 하고,
-- 그 사이 가입한 사용자는 빠진다. 예외만 담으면 그런 일이 없다.
CREATE TABLE notification_mutes (
    user_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    muted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, category)
);
