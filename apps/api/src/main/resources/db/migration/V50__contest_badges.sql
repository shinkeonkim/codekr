-- 대회 뱃지 (#463).
--
-- **대회에 나가도 뱃지가 붙지 않았다.** 규칙 엔진이 볼 수 있는 사건이 둘뿐이었고,
-- 그중 `PROBLEM_ACCEPTED` 는 대회 제출에도 돌지만 **규칙이 "대회였는가" 를 볼 방법이
-- 없었다.** 사건에 대회 id 를 실어 보내면서 지표 넷이 생겼다:
-- `is_contest_submission` · `contest_registered_count` · `contest_participated_count` ·
-- `contest_accepted_count`.
--
-- 여기 두 개는 **그 지표가 실제로 도는지 보여 주는 것**이기도 하다. 나머지는 어드민이
-- 배포 없이 만든다 (#201, #203).
INSERT INTO badges (code, label, description, sort_order, rule_key) VALUES
('CONTEST_FIRST',  '첫 대회',   '대회에서 처음으로 문제를 맞혔습니다', 200, 'CONTEST_FIRST'),
('CONTEST_5',      '대회 단골', '서로 다른 대회 다섯 곳에서 문제를 맞혔습니다', 210, 'CONTEST_5');

INSERT INTO badge_rules (rule_key, event, conditions, group_by, code) VALUES
-- **맞혀야 받는다.** 등록만으로 주면 뱃지가 가벼워지고, 제출만으로 주면 "참여" 의
-- 증거로는 약하다 — 그 둘도 지표로는 있으므로 다른 규칙을 어드민이 만들 수 있다.
('CONTEST_FIRST', 'PROBLEM_ACCEPTED',
 '{"all": [{"measure": "is_contest_submission", "op": "==", "value": true}]}', NULL, 'CONTEST_FIRST'),
('CONTEST_5', 'PROBLEM_ACCEPTED',
 '{"all": [{"measure": "is_contest_submission", "op": "==", "value": true},
           {"measure": "contest_accepted_count", "op": ">=", "value": 5}]}', NULL, 'CONTEST_5');
