-- 로그인하지 못하는 사람의 신고 (#611).
--
-- **가장 급한 신고가 로그인 바깥에 있다** — "가입이 안 됩니다", "인증 메일이 안 옵니다",
-- "비밀번호 재설정이 안 됩니다". 이 사람들은 로그인을 못 해서 신고하려는 것인데,
-- #603 이 만든 통로는 로그인을 요구했다.
--
-- **`reporter_id` 를 nullable 로 연다.** 비회원에게 가짜 사용자 행을 만들어 붙이는 방법도
-- 있지만, 그러면 회원 수·랭킹·활동 집계가 전부 그 행을 걸러야 한다 — 한 곳을 편하게
-- 하려고 열 곳을 어렵게 만드는 셈이다.
ALTER TABLE site_feedbacks ALTER COLUMN reporter_id DROP NOT NULL;

-- **누가 넣었는지 대신 어디서 왔는지를 남긴다.** 답을 돌려주지 않기로 했으므로(#611)
-- 연락처는 받지 않는다. 남용을 되짚을 때 필요한 것은 그때의 출처뿐이다.
ALTER TABLE site_feedbacks ADD COLUMN reporter_hint VARCHAR(60);

COMMENT ON COLUMN site_feedbacks.reporter_id IS '넣은 회원. 비회원이 넣었으면 NULL (#611)';
COMMENT ON COLUMN site_feedbacks.reporter_hint IS '비회원이 넣었을 때의 출처 힌트. 주소를 그대로 두지 않는다 (#611)';
