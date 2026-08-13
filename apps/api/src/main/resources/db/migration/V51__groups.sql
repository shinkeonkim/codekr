-- 그룹 (#401, #240 6단계).
--
-- **소속과 다른 표다** (기획서 2절). 소속은 어드민이 만들고 메일 도메인이 가입을
-- 정하지만, 그룹은 누구나 만들고 사람이 사람을 부른다. 신뢰 수준이 다른 것을 한 표에
-- 담으면 화면이 그 둘을 구분해 보여 줄 수 없다 — "서울대학교" 라는 이름의 그룹이
-- 진짜 서울대 랭킹 옆에 같은 모양으로 놓인다.
CREATE TABLE groups (
    id          BIGSERIAL PRIMARY KEY,
    -- **소속 이름과 같아도 막지 않는다.** 막으려면 기관 이름 목록이 필요하고 그것은
    -- 유지될 수 없다. 대신 화면이 소속과 그룹을 절대 같은 목록에 섞지 않는다.
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(200) NOT NULL DEFAULT '',
    -- 방장. 만든 사람이고, 넘길 수 있다 — 방장이 떠나면 그룹이 잠긴다.
    owner_id    BIGINT       NOT NULL REFERENCES users (id),
    -- **초대 링크가 기본이다.** 처음부터 공개면 스팸 가입이 온다. 방장이 켤 수 있다.
    open_join   BOOLEAN      NOT NULL DEFAULT false,
    -- 초대 링크의 토큰. 방장이 새로 뽑으면 옛 링크는 그 자리에서 죽는다.
    invite_token VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 어드민이 내리거나 방장이 해산하면 채운다. 행은 지우지 않는다 (ADR-0007).
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_groups_invite_token ON groups (invite_token);
CREATE INDEX idx_groups_owner ON groups (owner_id);

CREATE TABLE group_members (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES groups (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 두 번 들어갈 수 없다.
CREATE UNIQUE INDEX uq_group_members ON group_members (group_id, user_id);
CREATE INDEX idx_group_members_user ON group_members (user_id);
