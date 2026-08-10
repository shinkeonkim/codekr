-- 사용자마다 제출 소스의 기본 공개 범위를 정할 수 있게 한다 (#104).
--
-- 지금은 모두에게 PRIVATE 고정이라, 늘 공개하고 싶은 사람은 제출할 때마다 바꿔야 하고
-- 한 번 잊으면 의도와 다른 범위로 남는다. 매번 고르게 하는 것은 선택지를 준 것이 아니라
-- 실수할 기회를 준 것이다.
--
-- 기존 사용자는 PRIVATE — 지금 동작을 그대로 유지한다. 설정을 도입했다고 이미 낸 제출의
-- 공개 범위가 바뀌면 안 된다.
ALTER TABLE users
    ADD COLUMN default_submission_visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'
        CHECK (default_submission_visibility IN ('PUBLIC', 'PRIVATE', 'ACCEPTED_ONLY'));
