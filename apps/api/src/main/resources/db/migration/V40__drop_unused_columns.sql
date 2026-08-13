-- 안 쓰는 컬럼·표를 지운다 (#266).
--
-- #207 이 랭킹 비참여 설정을, #199 가 알림 끄기를 **코드에서** 걷어냈다. 스키마를 그때
-- 함께 지우지 않은 이유는 되돌릴 수 있어야 하기 때문이다 (#246 의 expand/contract) —
-- 지금 배포에서 컬럼을 지우면 그 배포를 되돌렸을 때 **옛 이미지가 뜨지 못한다.**
-- 옛 코드의 엔티티가 그 컬럼을 매핑하고, Hibernate 는 `ddl-auto: validate` 라
-- 없는 컬럼을 보면 기동에서 실패한다.
--
-- 그 둘이 배포된 것을 확인하고(배포 태그 d5ccf69 가 #207·#199 를 포함한다) 지운다.
--
-- **셋을 한 마이그레이션에 묶는다.** 배포마다 하나씩 지우면 그 사이 배포들이 전부
-- "지우는 배포" 가 되고, 되돌릴 수 있는 지점을 세기 어려워진다.
--
-- 지우기 전 값: `ranking_opt_out = true` 0행, `notification_mutes` 0행,
-- `view_notification_enabled = true` 는 로컬 1 · 홈랩 0 — 켜 둔 사람이 없다.
ALTER TABLE users DROP COLUMN IF EXISTS ranking_opt_out;
ALTER TABLE users DROP COLUMN IF EXISTS view_notification_enabled;
DROP TABLE IF EXISTS notification_mutes;
