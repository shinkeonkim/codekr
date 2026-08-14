-- 대회 공개 범위 (#465).
--
-- 전에는 목록에 띄우거나(`PUBLISHED`) 아무도 못 들어오거나(`DRAFT`) 둘뿐이었다.
-- 그 사이 — **"열려 있지만 목록에는 없는"** 대회 — 가 없어서 스터디·사내 대회를 낼 수
-- 없었다.
--
-- **`status` 와 별개 값이다.** 합치면 "준비 중인 비공개 대회" 와 "열린 비공개 대회" 를
-- 가를 수 없다. 문제집(#87)이 같은 문제를 먼저 풀었고 그 이름을 그대로 쓴다.
ALTER TABLE contests
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('UNLISTED', 'PUBLIC'));

-- **기존 대회는 전부 공개다.** 지금 열려 있는 것을 조용히 숨기면 참가자가 잃어버린다.
COMMENT ON COLUMN contests.visibility IS '#465 — UNLISTED 는 목록에 없을 뿐 비밀이 아니다';
