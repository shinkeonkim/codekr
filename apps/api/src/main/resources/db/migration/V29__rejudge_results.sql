-- 재채점의 판정 전이를 남긴다 (#187).
--
-- 전에는 `submissions.previous_verdict` 를 마감 시점에 지웠다. 바뀌었는지 여부만 알고
-- **무엇에서 무엇으로 바뀌었는지는 사라졌다.** 판정이 뒤집힌 사람에게 설명할 근거가
-- 남지 않는다는 뜻이다.
CREATE TABLE rejudge_submission_results (
    batch_id         BIGINT      NOT NULL REFERENCES rejudge_batches (id) ON DELETE CASCADE,
    submission_id    BIGINT      NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL,
    -- 재채점 전 판정. 채점이 끝나지 않은 제출을 재채점했다면 없을 수 있다.
    previous_verdict VARCHAR(30),
    new_verdict      VARCHAR(30),
    score_delta      INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 같은 제출의 결과가 두 번 오면(재전달) 덮어쓴다. 두 줄이 되면 집계가 어긋난다.
    PRIMARY KEY (batch_id, submission_id)
);

CREATE INDEX idx_rejudge_results_batch_user ON rejudge_submission_results (batch_id, user_id);

-- 배치가 언제 끝났는지 알아야 사람별 요약을 보낼 수 있다.
ALTER TABLE rejudge_batches ADD COLUMN processed_count INT NOT NULL DEFAULT 0;
