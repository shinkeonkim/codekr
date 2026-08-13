-- 뱃지 달성 규칙 (#202). 설계는 #200 의 기획서를 따른다.
--
-- 조건이 `BadgeAwarder.kt` 의 SQL 에 박혀 있었다 — 새 뱃지를 만들려면 코드를 고쳤다.
-- **조건의 재료(지표)는 코드가 정하고, 조합·임계값·문구는 데이터가 정한다** (#200 §4).
CREATE TABLE badge_rules (
    id         BIGSERIAL PRIMARY KEY,
    -- 규칙 키. 뱃지 정의(#201)의 `rule_key` 가 이것을 가리킨다.
    rule_key   VARCHAR(60) NOT NULL UNIQUE,
    -- 언제 도는가 (#200 §3). 이 이벤트를 구독하는 규칙만 돌린다.
    event      VARCHAR(40) NOT NULL,
    /*
        조건 (#200 §4.2).

        `{"all": [{"measure": "...", "op": ">=", "value": 10}]}` 모양이다.
        **자유 SQL 이 들어갈 자리가 없다** — 지표는 코드가 정한 목록에서만 고른다.
    */
    conditions JSONB       NOT NULL DEFAULT '{"all": []}'::jsonb,
    -- 파라미터화된 뱃지 (#200 §5). 이번 이벤트가 속한 그룹 하나만 본다.
    group_by   VARCHAR(40),
    -- 부여할 코드. `{group}` 만 치환한다.
    code       VARCHAR(60) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_badge_rules_event ON badge_rules (event) WHERE enabled;

-- 지금 있는 넷을 규칙으로 옮긴다 (#200 §5 에 적어 둔 그대로).
INSERT INTO badge_rules (rule_key, event, conditions, group_by, code) VALUES
-- 조건이 비어 있으면 **이벤트가 곧 달성**이다. 부여가 멱등이라 "처음" 이 저절로 성립한다.
('FIRST_ACCEPT', 'PROBLEM_ACCEPTED', '{"all": []}', NULL, 'FIRST_ACCEPT'),
('FIRST_SOLVER', 'PROBLEM_ACCEPTED',
 '{"all": [{"measure": "is_first_solver", "op": "==", "value": true}]}', NULL, 'FIRST_SOLVER'),
('CATEGORY_10', 'PROBLEM_ACCEPTED',
 '{"all": [{"measure": "accepted_in_category", "op": ">=", "value": 10}]}',
 'problem_category', 'CATEGORY_10_{group}'),
('STREAK_7', 'STREAK_UPDATED',
 '{"all": [{"measure": "longest_streak_days", "op": ">=", "value": 7}]}', NULL, 'STREAK_7'),
('STREAK_30', 'STREAK_UPDATED',
 '{"all": [{"measure": "longest_streak_days", "op": ">=", "value": 30}]}', NULL, 'STREAK_30');

-- 카테고리 뱃지 일곱은 정의(#201)에서 같은 규칙 키를 가리킨다.
UPDATE badges SET rule_key = 'CATEGORY_10' WHERE code LIKE 'CATEGORY_10_%';

/*
    무엇이 언제 왜 주어졌는가 (#202).

    **안 주면 "왜 안 나왔는지" 를 물었을 때 답할 수 없다.** 준 것만 남기면 그 질문에
    답하지 못하므로, 규칙이 돌아간 사실 자체를 남긴다.
*/
CREATE TABLE badge_awards_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    rule_key   VARCHAR(60) NOT NULL,
    code       VARCHAR(60),
    -- 조건을 만족했는가. false 면 왜 안 나왔는지의 답이 된다.
    matched    BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_badge_awards_log_user ON badge_awards_log (user_id, created_at DESC);
