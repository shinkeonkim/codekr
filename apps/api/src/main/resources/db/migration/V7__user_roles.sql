-- 사용자 역할을 표로 분리한다 (#103).
--
-- 한 사람이 여러 역할을 가질 수 있다 — 문제도 내고 게시판도 보는 사람이 있다.
-- users.role 한 칸으로는 표현할 수 없다.
--
-- **자원 범위(어느 대회의 관리자인가)는 아직 없다.** 대회 도메인이 생길 때
-- scope 컬럼을 더한다 (#61). 지금 넣으면 쓰이지 않는 채로 남고, 대회의 실제 요구를
-- 보기 전이라 모양이 틀릴 가능성이 크다.
CREATE TABLE user_roles (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(30) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role)
);

CREATE INDEX idx_user_roles_role ON user_roles (role);

-- 기존 역할을 그대로 옮긴다. USER 는 모두가 갖고, ADMIN 이었던 계정은 ADMIN 도 갖는다.
INSERT INTO user_roles (user_id, role)
SELECT id, 'USER' FROM users;

INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE role = 'ADMIN';

-- 진실이 두 곳에 있으면 반드시 갈라진다. 옮겼으면 원래 칸은 지운다.
ALTER TABLE users DROP COLUMN role;
