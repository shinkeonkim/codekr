-- 어드민 관리 기록 (#225).
--
-- **로그로는 안 된다.** 로그는 보관 기간이 지나면 사라지고(그때가 대개 물어보는 때다),
-- "이 회원에게 무슨 일이 있었나" 를 로그에서 모으는 것은 어드민의 일이 아니며,
-- 화면에서 볼 수 없으면 없는 것과 같다.
CREATE TABLE admin_audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    -- 누가
    actor_id     BIGINT      NOT NULL REFERENCES users (id),
    -- 무엇을
    action       VARCHAR(40) NOT NULL,
    -- 누구에게. 회원이 아닌 대상(게시글 등)이 생기면 target_type 이 갈린다.
    target_type  VARCHAR(20) NOT NULL,
    target_id    BIGINT      NOT NULL,
    /*
        그때의 대상 이름.

        **강제 탈퇴는 닉네임·이메일을 그 자리에서 지운다** (#140). 사본을 남기지 않으면
        기록이 "누구를" 지웠는지 말하지 못한다 — 남는 것은 숫자뿐이다.
    */
    target_label VARCHAR(100),
    -- 왜. 되돌릴 수 없는 것과 남에게 보이는 것에만 요구한다.
    reason       VARCHAR(500),
    -- 무엇이 바뀌었나 (역할 목록 등). 형태가 행위마다 달라 문자열로 둔다.
    detail       VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "이 회원에게 무슨 일이 있었나" 와 "이 어드민이 무엇을 했나" 를 모두 답해야 한다.
CREATE INDEX idx_admin_audit_target ON admin_audit_logs (target_type, target_id, id DESC);
CREATE INDEX idx_admin_audit_actor ON admin_audit_logs (actor_id, id DESC);

-- **덧붙이기만 되는 표다.** 고치거나 지우는 경로를 만들지 않는다 — 감사 기록의 뜻이
-- 거기에 있다. 정리 배치(ADR-0007)도 이 표를 건드리지 않는다.
