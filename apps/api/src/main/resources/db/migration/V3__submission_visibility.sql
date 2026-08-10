-- 제출 소스 코드의 공개 범위 (#33)
--
-- 기본값은 PRIVATE 다. 공개는 사용자가 명시적으로 선택해야 하는 행위이고,
-- 한 번 공개된 코드는 되돌려도 이미 읽힌 뒤일 수 있기 때문이다.
ALTER TABLE submissions
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';

-- 전체 제출 목록은 공개 범위와 판정으로 걸러 조회한다 (#34).
CREATE INDEX idx_submissions_explore ON submissions (kind, created_at DESC)
    WHERE deleted_at IS NULL;
