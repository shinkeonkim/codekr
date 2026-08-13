-- 공개 문제집 (#208).
--
-- 문제집(#87)은 **일부러 공개를 열지 않았다** — "누구나 공개 문제집을 만들 수 있게 하면
-- 스팸·중복·방치가 즉시 생기는데 지금은 신고도 정리도 할 수 없다" 는 근거였다.
--
-- **그 판단을 뒤집는다.** 링크 공유만으로는 아무도 새 문제집을 발견할 수 없고, 그러면
-- 문제집을 만드는 이유의 절반("남에게 도움이 되는 것")이 닿을 길이 없다.
-- 대신 **정리는 어드민이 한다** — 신고는 필요해지면 그때 만든다.
ALTER TABLE problem_collections DROP CONSTRAINT IF EXISTS problem_collections_visibility_check;
ALTER TABLE problem_collections ADD CONSTRAINT problem_collections_visibility_check
    CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC'));

-- 공개 목록은 최신순으로 훑는다. 인기순은 담은 사람 수나 조회수가 필요한데 둘 다 없다.
CREATE INDEX idx_problem_collections_public ON problem_collections (id DESC)
    WHERE visibility = 'PUBLIC' AND deleted_at IS NULL;
