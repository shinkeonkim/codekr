-- 뱃지 정의를 데이터로 (#201).
--
-- 이름과 설명이 `Badge.kt` 의 enum 에 있었다 — 오타 하나를 고치려고 배포했다.
-- 뱃지는 **운영이 손대는 물건인데 손댈 방법이 배포뿐**이었다.
--
-- 원래 판단("문구를 고치려고 마이그레이션하지 않는다")을 뒤집는 것이고, 그 대가는
-- **문구가 조용히 바뀔 수 있다**는 것이다 — 이미 받은 사람의 설명도 함께 바뀐다.
-- 사본을 두면 원래 구조로 돌아가므로, 바뀌는 것을 받아들이되 어드민 화면이 경고한다.
CREATE TABLE badges (
    -- **코드는 만들 때만 정하고 이후 잠근다.** `user_badges` 에 박히는 값이라
    -- 한 번 준 뒤에는 바꿀 수 없다.
    code        VARCHAR(60) PRIMARY KEY,
    label       VARCHAR(60) NOT NULL,
    description VARCHAR(200) NOT NULL,
    -- 숨김. **지우지 않는다** — 이미 받은 사람이 있다.
    visible     BOOLEAN     NOT NULL DEFAULT true,
    sort_order  INT         NOT NULL DEFAULT 0,
    /*
        어느 조건으로 주는가 (#201).

        지금은 조건이 코드에 있으므로 **코드 규칙의 이름**을 가리킨다. #200 의 DSL 이
        오면 이 자리가 규칙으로 대체된다 — 그때 갈아 끼울 수 있게 문자열로 둔다.
    */
    rule_key    VARCHAR(60) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_badges_visible ON badges (visible, sort_order, code);

-- 지금 있는 것들을 그대로 옮긴다.
--
-- **빈 상태로 시작하면 그 배포에서 아무도 뱃지를 못 받는다** (#200 §9).
INSERT INTO badges (code, label, description, sort_order, rule_key) VALUES
('FIRST_ACCEPT', '첫 정답', '처음으로 문제를 맞혔습니다', 10, 'FIRST_ACCEPT'),
('STREAK_7',     '일주일 연속', '7일 연속으로 문제를 풀었습니다', 20, 'STREAK_7'),
('STREAK_30',    '한 달 연속', '30일 연속으로 문제를 풀었습니다', 30, 'STREAK_30'),
('FIRST_SOLVER', '최초 해결자', '새로 올라온 문제를 가장 먼저 맞혔습니다', 40, 'FIRST_SOLVER');

-- 카테고리 뱃지는 **코드가 카테고리마다 갈라진다** (#201).
--
-- 하나의 규칙(`CATEGORY_10`)이 일곱 개의 정의를 만든다 — 정의를 일곱 줄로 펴 두는
-- 이유는, 그래야 어드민이 "SQL 문제 10개" 의 문구만 따로 고칠 수 있기 때문이다.
INSERT INTO badges (code, label, description, sort_order, rule_key) VALUES
('CATEGORY_10_ALGORITHM',      '알고리즘 10문제', '알고리즘 문제를 10개 맞혔습니다', 100, 'CATEGORY_10'),
('CATEGORY_10_DATA_STRUCTURE', '자료구조 10문제', '자료구조 문제를 10개 맞혔습니다', 101, 'CATEGORY_10'),
('CATEGORY_10_SQL',            'SQL 10문제', 'SQL 문제를 10개 맞혔습니다', 102, 'CATEGORY_10'),
('CATEGORY_10_NETWORK',        '네트워크 10문제', '네트워크 문제를 10개 맞혔습니다', 103, 'CATEGORY_10'),
('CATEGORY_10_LANGUAGE',       '프로그래밍 언어 10문제', '프로그래밍 언어 문제를 10개 맞혔습니다', 104, 'CATEGORY_10'),
('CATEGORY_10_OS',             '운영체제 10문제', '운영체제 문제를 10개 맞혔습니다', 105, 'CATEGORY_10'),
('CATEGORY_10_SYSTEM_DESIGN',  '시스템 설계 10문제', '시스템 설계 문제를 10개 맞혔습니다', 106, 'CATEGORY_10');
