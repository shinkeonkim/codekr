-- 대회 도메인 (#61).
--
-- **진행 상태를 저장하지 않는다.** SCHEDULED/RUNNING/ENDED 는 시작·종료 시각과 지금을
-- 견줘 조회 시점에 판정한다. 스케줄러가 상태를 옮기는 방식이면 스케줄러가 1분 늦는 순간
-- 대회가 1분 늦게 시작한다 — 지연이 곧 사고가 된다.
--
-- 저장하는 status 는 **운영자가 정하는 것**뿐이다.
CREATE TABLE contests (
    id                    BIGSERIAL PRIMARY KEY,
    slug                  VARCHAR(120) NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    description           TEXT         NOT NULL DEFAULT '',
    starts_at             TIMESTAMPTZ  NOT NULL,
    ends_at               TIMESTAMPTZ  NOT NULL,
    -- 종료 몇 분 전부터 순위를 동결할지 (#86). 0 이면 동결하지 않는다.
    freeze_minutes        INT          NOT NULL DEFAULT 30,
    -- 시작 후에도 참가 등록을 받을지.
    registration_open_during BOOLEAN   NOT NULL DEFAULT true,
    -- 종료 후 어드민이 최종 순위를 공개했는지 (#86). 자동이 아니다 —
    -- 종료 직후 발견된 문제를 바로잡을 틈이 필요하다.
    unfrozen_at           TIMESTAMPTZ,
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELED', 'ARCHIVED')),
    created_by            BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    CHECK (ends_at > starts_at)
);

CREATE UNIQUE INDEX uq_contests_slug ON contests (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_contests_starts_at ON contests (starts_at DESC) WHERE deleted_at IS NULL;

-- 대회에 배정된 문제와 배점.
--
-- 배점은 난이도에서 자동으로 뽑지 않고 어드민이 정한다 — 대회 설계의 자유도다.
CREATE TABLE contest_problems (
    contest_id BIGINT NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    problem_id BIGINT NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    -- 대회 안에서의 순번. 화면에는 A, B, C… 로 보인다.
    seq        INT    NOT NULL,
    score      INT    NOT NULL CHECK (score > 0),
    -- 문제가 잘못된 것으로 드러나면 제외한다. 지우지 않는 이유는 그 문제로 낸 제출이
    -- 남아 있기 때문이다 (#86 — 제외는 프리즈로 감추지 않고 즉시 반영한다).
    excluded_at TIMESTAMPTZ,
    PRIMARY KEY (contest_id, problem_id)
);

CREATE UNIQUE INDEX uq_contest_problems_seq ON contest_problems (contest_id, seq);

-- 참가 등록. **등록하지 않으면 문제를 볼 수 없다.**
CREATE TABLE contest_registrations (
    contest_id    BIGINT      NOT NULL REFERENCES contests (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (contest_id, user_id)
);
