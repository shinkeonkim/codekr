-- 문제의 출제자·검수자·출처 (#236).
--
-- 문제가 어디서 왔고 누가 만들었는지 남지 않았다. 잘못된 문제를 누구에게 물어야 하는지
-- 모르고, **출처를 적을 칸이 없으니 적을 수 없고, 적을 수 없으니 안 적는 것이 기본**이
-- 됐다 — AI 로 지문을 붙여 넣는 도구(#230)가 생기면 더 커질 문제다.

-- **처음부터 다대다다.** 문제 하나를 둘이 만드는 일은 흔하고 검수는 더 그렇다.
-- 한 명으로 시작하면 늘리는 마이그레이션이 또 필요하다.
CREATE TABLE problem_credits (
    problem_id BIGINT      NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SETTER(출제) | REVIEWER(검수)
    --
    -- **검수는 정답 코드 검증(#39)과 다르다.** 그것은 기계가 하는 것이고 이것은
    -- 사람이 읽어 본 것이다 — 버튼 한 번으로 자동으로 채우지 않는다.
    role       VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (problem_id, user_id, role)
);

CREATE INDEX idx_problem_credits_user ON problem_credits (user_id, role);

-- 출처는 **사람이 아니다.** 바깥의 무언가를 가리키므로 회원 참조가 아니라 라벨과 링크다.
--
-- **라벨과 링크는 한 쌍이다** — 링크만 있으면 무엇인지 모르고, 라벨만 있으면 확인할 수
-- 없다. 다만 링크 없는 출처(책·대회 이름)는 있으므로 링크만 널 허용이다.
ALTER TABLE problems ADD COLUMN source_label VARCHAR(200);
ALTER TABLE problems ADD COLUMN source_url   VARCHAR(500);
