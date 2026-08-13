-- 고른 화면 테마를 계정에도 저장한다 (#274).
--
-- **NULL 이 "고르지 않음" 이다.** 기본값을 박으면 나중에 기본값을 바꿀 때 이미 저장된
-- 사람들이 그것을 이긴다 — 아무것도 고른 적 없는 사람이 옛 기본값에 묶인다.
ALTER TABLE users ADD COLUMN theme VARCHAR(16);

ALTER TABLE users
    ADD CONSTRAINT users_theme_check CHECK (theme IS NULL OR theme IN ('LIGHT', 'DARK', 'SYSTEM'));
