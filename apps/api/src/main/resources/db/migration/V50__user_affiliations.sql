-- 사람에게 붙은 소속 (#398, #240 3단계).
--
-- **소속은 여럿이다** (기획서 4절). 학부와 대학원, 학교와 회사를 동시에 가질 수 있다.
-- 주 소속을 고르게 하지 않는다 — 고르게 하면 **실제로 둘인 사람에게 하나를 부인하라고
-- 요구하는 것**이고, 그렇게 얻는 것은 질의가 조금 단순해지는 것뿐이다.
CREATE TABLE user_affiliations (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    affiliation_id BIGINT      NOT NULL REFERENCES affiliations (id),
    -- **어느 주소로 붙였는지.** 그 주소를 떼면 이 소속도 함께 떨어져야 한다 —
    -- 붙인 근거가 사라졌기 때문이다.
    user_email_id  BIGINT      NOT NULL REFERENCES user_emails (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 같은 소속을 두 번 붙일 이유가 없다. 두 주소가 같은 소속을 가리켜도 한 번이다.
CREATE UNIQUE INDEX uq_user_affiliations ON user_affiliations (user_id, affiliation_id);
CREATE INDEX idx_user_affiliations_affiliation ON user_affiliations (affiliation_id);
