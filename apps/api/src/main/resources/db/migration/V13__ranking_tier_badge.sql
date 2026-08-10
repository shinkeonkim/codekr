-- 실력 티어·뱃지·랭킹 비참여 (#58).

-- 랭킹에 이름이 오르는 것을 원하지 않는 사람이 있다. 끄면 목록에서 빠진다.
-- 점수는 계속 쌓는다 — 껐다 켤 때 기록이 사라지면 끄기가 되돌릴 수 없는 선택이 된다.
ALTER TABLE users ADD COLUMN ranking_opt_out BOOLEAN NOT NULL DEFAULT false;

-- 도달했던 최고 점수. **실력 티어는 이 값으로 정한다 — 강등이 없기 때문이다.**
-- 재채점(#107)으로 점수가 내려가도 티어는 유지된다. 학습 도구에서 강등은 이탈 사유고,
-- 우리 잘못(잘못된 테스트케이스)으로 남의 티어를 깎는 것은 더욱 그렇다.
ALTER TABLE users ADD COLUMN peak_score INT NOT NULL DEFAULT 0;

-- 뱃지는 **행동 기반**으로만 준다. 점수 기반 뱃지는 티어와 중복이다.
CREATE TABLE user_badges (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 코드로 저장한다. 이름·설명은 애플리케이션이 갖는다 — 문구를 고치려고 마이그레이션하지 않는다.
    code       VARCHAR(40) NOT NULL,
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, code)
);

CREATE INDEX idx_user_badges_awarded_at ON user_badges (user_id, awarded_at DESC);
