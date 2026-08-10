-- 코드.kr 초기 스키마 (docs/02_도메인_모델.md 참고)
--
-- 소프트 삭제 규칙: deleted_at 이 NULL 인 행만 살아 있는 것으로 본다.
-- 자식 테이블의 유니크 제약은 "살아 있는 행"에만 걸어야, 같은 순번/런타임을 다시 등록할 수 있다.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(30)  NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE problems (
    id                 BIGSERIAL PRIMARY KEY,
    slug               VARCHAR(120) NOT NULL,
    title              VARCHAR(200) NOT NULL,
    category           VARCHAR(30)  NOT NULL,
    -- 난이도는 1(브론즈 5) ~ 30(루비 1). 정렬과 티어 범위 검색을 위해 정수로 저장한다.
    difficulty_level   INT          NOT NULL CHECK (difficulty_level BETWEEN 1 AND 30),
    description        TEXT         NOT NULL,
    input_description  TEXT,
    output_description TEXT,
    time_limit_ms      INT          NOT NULL DEFAULT 2000,
    memory_limit_mb    INT          NOT NULL DEFAULT 256,
    published          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by         BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);

-- slug 도 살아 있는 문제 안에서만 유일하면 된다 (삭제한 slug 를 다시 쓸 수 있어야 한다).
CREATE UNIQUE INDEX uq_problems_slug ON problems (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_problems_listing ON problems (published, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_problems_filter ON problems (category, difficulty_level) WHERE deleted_at IS NULL;

CREATE TABLE problem_testcases (
    id              BIGSERIAL PRIMARY KEY,
    problem_id      BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    seq             INT         NOT NULL,
    input           TEXT        NOT NULL,
    expected_output TEXT        NOT NULL,
    visibility      VARCHAR(10) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

-- 살아 있는 테스트케이스 안에서만 순번이 유일하면 된다.
CREATE UNIQUE INDEX uq_testcase_seq ON problem_testcases (problem_id, seq) WHERE deleted_at IS NULL;

CREATE TABLE problem_templates (
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    runtime_id  VARCHAR(40) NOT NULL,
    source_code TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

-- 문제 하나에 실행 환경별 초기 코드는 하나뿐이다.
CREATE UNIQUE INDEX uq_template_runtime ON problem_templates (problem_id, runtime_id) WHERE deleted_at IS NULL;

CREATE TABLE submissions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_id     BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    runtime_id     VARCHAR(40) NOT NULL,
    source_code    TEXT        NOT NULL,
    status         VARCHAR(20) NOT NULL,
    verdict        VARCHAR(30),
    passed_count   INT         NOT NULL DEFAULT 0,
    total_count    INT         NOT NULL DEFAULT 0,
    max_runtime_ms INT         NOT NULL DEFAULT 0,
    max_memory_kb  INT         NOT NULL DEFAULT 0,
    compile_error  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);

CREATE INDEX idx_submissions_user ON submissions (user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_submissions_problem ON submissions (problem_id, created_at DESC) WHERE deleted_at IS NULL;
-- 스위퍼가 오래 머문 제출을 찾을 때 사용한다.
CREATE INDEX idx_submissions_status ON submissions (status, created_at);

CREATE TABLE submission_testcase_results (
    id             BIGSERIAL PRIMARY KEY,
    submission_id  BIGINT      NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    seq            INT         NOT NULL,
    verdict        VARCHAR(30) NOT NULL,
    runtime_ms     INT         NOT NULL DEFAULT 0,
    memory_kb      INT         NOT NULL DEFAULT 0,
    stderr_excerpt TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 이벤트가 재전달되어도 결과가 중복되지 않게 한다 (ADR-0004).
    CONSTRAINT uq_result_seq UNIQUE (submission_id, seq)
);
