-- 재채점 (#107).
--
-- 테스트케이스가 잘못됐다가 고쳐지면 기존 제출을 다시 채점해야 한다. 이건 대회만의 일이
-- 아니라 평소 문제에도 필요하다.
CREATE TABLE rejudge_batches (
    id           BIGSERIAL   PRIMARY KEY,
    problem_id   BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    -- 사용자에게 "왜 바뀌었는지" 알릴 때 그대로 쓴다. 그래서 필수다.
    reason       VARCHAR(200) NOT NULL,
    requested_by BIGINT      NOT NULL,
    target_count INT         NOT NULL,
    changed_count INT        NOT NULL DEFAULT 0,
    finished_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rejudge_batches_problem ON rejudge_batches (problem_id, created_at DESC);

-- 재채점 중인 제출은 어느 배치에 속하는지와 **이전 판정**을 들고 있는다.
--
-- 이전 판정을 저장하는 이유: 결과가 도착했을 때 "바뀌었는가" 를 알아야 알림을 보낼지
-- 정할 수 있다. 안 바뀐 사람에게 보내면 소음이다.
ALTER TABLE submissions
    ADD COLUMN rejudge_batch_id  BIGINT REFERENCES rejudge_batches (id) ON DELETE SET NULL,
    ADD COLUMN previous_verdict  VARCHAR(30);

CREATE INDEX idx_submissions_rejudge ON submissions (rejudge_batch_id)
    WHERE rejudge_batch_id IS NOT NULL;
