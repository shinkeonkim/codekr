-- 참가 승인 (#466).
--
-- 전에는 **등록에 상태가 없었다.** 행이 있으면 참가, 없으면 미참가 — 그 사이가 없어서
-- "자격을 확인하고 받는 대회" 를 낼 수 없었다.
--
-- **공개 범위(#465)와 직교한다.** 그쪽은 *누가 보는가*, 이쪽은 *누가 내는가* 다.
-- 네 조합이 다 쓸모가 있고, 한 값으로 합치면 **"링크를 아는 사람은 다 참가"**(스터디)가
-- 사라진다.
ALTER TABLE contests
    ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE contest_registrations
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED'
        CHECK (status IN ('PENDING', 'APPROVED')),
    ADD COLUMN decided_at TIMESTAMPTZ;

-- **기존 등록은 전부 승인된 것이다.** 지금 참가 중인 사람이 갑자기 대기가 되면
-- 대회 도중에 제출이 막힌다. 기본값이 그것을 막는다.
--
-- 거절은 **행을 지운다** — 남기면 "왜 거절됐는지" 를 물을 때 답할 것이 필요하고,
-- 그 답은 관리 기록(#225)에 있다. 그리고 지워야 다시 신청할 수 있다.
CREATE INDEX idx_contest_registrations_pending
    ON contest_registrations (contest_id) WHERE status = 'PENDING';
