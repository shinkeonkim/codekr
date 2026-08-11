-- 대회마다 제출 간격을 조정할 수 있게 한다 (#189).
--
-- 전에는 20초로 코드에 박혀 있었다. 대회 성격에 따라 짧게 두고 싶을 수 있는데,
-- **0 은 허용하지 않는다** — 제한이 없는 것과 같아지고 한 참가자가 채점 차선을
-- 혼자 채울 수 있다. 하한 3초는 애플리케이션과 이 제약 양쪽에서 지킨다.
ALTER TABLE contests
    ADD COLUMN submission_cooldown_seconds INT NOT NULL DEFAULT 20
        CHECK (submission_cooldown_seconds >= 3);
