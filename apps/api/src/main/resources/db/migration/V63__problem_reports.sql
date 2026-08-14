-- 문제 오류 신고 (#478).
--
-- **질문(#139)과 다른 것이다.** 질문은 다른 사용자가 답하면 끝나지만, 오류 신고는
-- **문제를 고쳐야 끝난다.** 그리고 안 보고 넘기면 그 사람만 아쉬운 것이 아니라
-- **모든 제출이 계속 잘못 채점된다.** 섞어 두면 질문 백 개 사이에 신고 하나가 묻힌다.
CREATE TABLE problem_reports (
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    reporter_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 무엇이 잘못됐는가. 어드민이 목록에서 고르는 기준이 된다.
    kind        VARCHAR(30) NOT NULL,
    body        TEXT        NOT NULL,
    -- OPEN → ACCEPTED(고쳤다) | REJECTED(문제가 아니다)
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    -- 처리하며 남긴 말. **거절에는 반드시 있어야 한다** — 이유 없는 거절은
    -- 신고한 사람에게 "읽지 않았다" 와 구분되지 않는다.
    resolution  TEXT,
    resolved_by BIGINT REFERENCES users (id),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 어드민은 "아직 안 본 것" 부터 본다.
CREATE INDEX idx_problem_reports_open ON problem_reports (status, created_at DESC);
CREATE INDEX idx_problem_reports_problem ON problem_reports (problem_id);
