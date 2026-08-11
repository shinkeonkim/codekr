-- 대회 제출의 감사 이력 (#148).
--
-- ## 무엇을 얼마나 남기는가
--
-- **IP 는 개인정보다.** 남기기로 한 이상 얼마나 두는지, 누가 보는지를 함께 정해야 한다.
--
-- | 항목 | 남기는 이유 |
-- |---|---|
-- | 접수 시각 | 마감 판정의 근거. 이미 submissions.created_at 에 있으므로 여기 두지 않는다 |
-- | IP | 여러 계정이 한 곳에서 제출했는지 |
-- | User-Agent | 자동화 도구인지 |
--
-- **대회 제출만 남긴다.** 평소 제출까지 남기면 모든 사용자의 접속 이력이 쌓이는데,
-- 그것은 이 기능이 필요로 하지 않는 정보다.
--
-- 보관 기간은 대회 종료 후 90일이다. 이의 제기와 표절 검토가 그 안에 끝난다고 본다.
CREATE TABLE contest_submission_audits (
    submission_id BIGINT      PRIMARY KEY REFERENCES submissions (id) ON DELETE CASCADE,
    contest_id    BIGINT      NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- IPv6 를 담을 수 있어야 한다.
    ip            VARCHAR(45),
    user_agent    VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "같은 IP 에서 여러 계정이 냈는가" 가 가장 먼저 보는 축이다.
CREATE INDEX idx_contest_audits_ip ON contest_submission_audits (contest_id, ip);
CREATE INDEX idx_contest_audits_created ON contest_submission_audits (created_at);
