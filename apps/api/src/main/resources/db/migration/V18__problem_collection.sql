-- 문제집 (#87).
--
-- **회원 누구나 만들 수 있다.** 어드민 전용이 아니다 — 커리큘럼을 만드는 것은
-- 가르치는 사람만의 일이 아니고, 자기 복습 목록을 만드는 것이 더 흔하다.
CREATE TABLE problem_collections (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    -- 1차에는 비공개와 링크 공유뿐이다. 공개 목록은 두지 않는다 —
    -- 스팸·중복·방치를 정리할 도구가 없는 채로 열면 목록이 곧 쓸 수 없게 된다.
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE'
        CHECK (visibility IN ('PRIVATE', 'UNLISTED')),
    -- 링크 공유용 식별자. id 를 쓰면 번호를 바꿔 가며 남의 문제집을 찾을 수 있다.
    share_token VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_problem_collections_token ON problem_collections (share_token);
CREATE INDEX idx_problem_collections_owner ON problem_collections (owner_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- 담긴 문제와 순서.
CREATE TABLE problem_collection_items (
    collection_id BIGINT NOT NULL REFERENCES problem_collections (id) ON DELETE CASCADE,
    problem_id    BIGINT NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    seq           INT    NOT NULL,
    PRIMARY KEY (collection_id, problem_id)
);

CREATE UNIQUE INDEX uq_problem_collection_items_seq ON problem_collection_items (collection_id, seq);
