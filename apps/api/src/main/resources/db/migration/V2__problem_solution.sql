-- 문제의 정답 코드와 테스트케이스 검증 (#39)
--
-- 정답 코드는 어드민만 볼 수 있어야 하므로 문제 테이블에 두되, 공개 응답 DTO 에는
-- 필드 자체를 만들지 않는다 (히든 테스트케이스와 같은 방식).

ALTER TABLE problems
    ADD COLUMN solution_runtime_id VARCHAR(40),
    ADD COLUMN solution_source_code TEXT,
    -- 마지막 검증 실행. submissions 를 그대로 재사용한다 (아래 kind 참고).
    ADD COLUMN verification_submission_id BIGINT REFERENCES submissions (id) ON DELETE SET NULL,
    -- 검증 시점의 "채점에 영향을 주는 내용"의 지문. 지금 값과 다르면 그 결과는 낡은 것이다.
    -- 수정 시각을 쓰지 않는 이유: 검증 기록을 저장하는 것만으로 수정 시각이 바뀌어
    -- 결과가 항상 낡은 것으로 표시된다.
    ADD COLUMN verified_signature VARCHAR(64);

-- 정답 코드 검증도 사용자 제출과 같은 채점 파이프라인을 쓴다.
-- 다만 사용자에게 보이는 목록·통계에는 절대 섞이면 안 되므로 종류를 구분한다.
ALTER TABLE submissions
    ADD COLUMN kind VARCHAR(30) NOT NULL DEFAULT 'USER';

-- 사용자용 목록 조회는 항상 kind 로 걸러진다.
CREATE INDEX idx_submissions_user_kind ON submissions (user_id, kind, created_at DESC)
    WHERE deleted_at IS NULL;
