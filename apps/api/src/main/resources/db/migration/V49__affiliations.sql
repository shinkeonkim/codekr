-- 소속과 그 메일 도메인 (#397, #240 2단계).
--
-- **어드민이 등록한다.** 자동으로 만들면 `@gmail.com` 이 "지메일 대학" 이 되고
-- 오타 도메인이 소속으로 쌓인다 — 소속은 몇 개 안 되고 자주 늘지 않으므로
-- 손으로 넣는 비용이 작다.
CREATE TABLE affiliations (
    id         BIGSERIAL PRIMARY KEY,
    -- 사람이 읽는 이름. 바꾸면 이미 붙은 사람들의 표시가 함께 바뀐다.
    name       VARCHAR(100) NOT NULL,
    -- SCHOOL | COMPANY. 화면이 나눠 보여 준다.
    kind       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- 같은 이름이 둘이면 사람이 어느 것을 고를지 알 수 없다.
CREATE UNIQUE INDEX uq_affiliations_name ON affiliations (name) WHERE deleted_at IS NULL;

CREATE TABLE affiliation_domains (
    id             BIGSERIAL PRIMARY KEY,
    affiliation_id BIGINT       NOT NULL REFERENCES affiliations (id),
    -- 소문자로만 저장한다. `SNU.ac.kr` 과 `snu.ac.kr` 이 다른 도메인이 되면 안 된다.
    domain         VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- **한 도메인은 한 소속에만 붙는다.** 둘에 붙으면 같은 메일로 두 소속을 얻는다.
CREATE UNIQUE INDEX uq_affiliation_domains ON affiliation_domains (domain);
CREATE INDEX idx_affiliation_domains_affiliation ON affiliation_domains (affiliation_id);
