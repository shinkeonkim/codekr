-- 프로필 아바타 (#116).
--
-- 오브젝트 키만 저장한다. URL 을 저장하지 않는 이유: 저장소 주소나 서빙 경로가 바뀌면
-- 모든 행을 고쳐야 하고, 그 사이의 값은 깨진 링크가 된다.
ALTER TABLE users ADD COLUMN avatar_key VARCHAR(120);
