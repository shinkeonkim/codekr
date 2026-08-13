-- 부르는 이름과 가리키는 주소를 나눈다 (#307).
--
-- 지금은 닉네임 하나가 **표시 이름이자 주소**다. 그래서 이름을 바꾸면 프로필 주소가
-- 바뀌고, 주고받은 링크와 검색 색인(#278)이 끊긴다 — 그래서 지금은 **이름을 아예
-- 바꿀 수 없다.** #204 가 문제에서 같은 것을 나눴다(가리키는 것은 번호, 부르는 것은 제목).

-- **`handle` 은 바뀌지 않는다.** 이것이 주소다.
ALTER TABLE users ADD COLUMN handle VARCHAR(30);

/*
    기존 사용자의 handle 을 만든다.

    지금 닉네임은 한글·공백·특수문자가 섞여 있어 그대로 handle 이 될 수 없다.
    규칙: 소문자·숫자·하이픈만 남기고, 남는 것이 없거나 너무 짧으면 `user{id}` 로 둔다.
    충돌하면 뒤에 id 를 붙인다 — **id 는 유일하므로 이 방법은 반드시 끝난다.**
*/
UPDATE users SET handle = regexp_replace(lower(nickname), '[^a-z0-9-]', '', 'g');
UPDATE users SET handle = NULL WHERE length(coalesce(handle, '')) < 2;
UPDATE users u SET handle = 'user' || u.id
WHERE u.handle IS NULL
   OR EXISTS (SELECT 1 FROM users other WHERE other.handle = u.handle AND other.id < u.id);

/*
    **빈 주소로 저장되는 길을 막는다.**

    엔티티를 거치지 않고 넣는 자리(시드·시험·마이그레이션)가 있고, 그때 handle 이
    비면 NOT NULL 이 500 이 된다. 기본값을 임의값으로 두면 **어떤 경로로 넣어도
    유일한 주소가 생긴다** — 사람이 읽기 좋은 값은 아니지만, 없는 것보다 낫다.
*/
ALTER TABLE users ALTER COLUMN handle SET DEFAULT
    ('u' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 20));
ALTER TABLE users ALTER COLUMN handle SET NOT NULL;
CREATE UNIQUE INDEX idx_users_handle ON users (handle);
