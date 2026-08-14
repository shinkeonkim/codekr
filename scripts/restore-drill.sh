#!/usr/bin/env bash
# 복원 연습 — 걸리는 시간을 잰다 (#281).
#
# **해 보지 않은 절차로 1시간을 약속할 수 없다.** RTO 는 지금 근거가 없는 숫자이고,
# 이 스크립트는 그것을 숫자로 바꾸는 일만 한다.
#
# 하는 일은 넷이다.
#   1. 빈 데이터베이스를 만든다 (원본을 건드리지 않는다)
#   2. 덤프를 복원하며 **시간을 잰다**
#   3. 표와 행 수를 원본과 견준다
#   4. 연습용 데이터베이스를 지운다
#
# 운영 덤프로 한 번 돌리고 그 시간을 `docs/09` §5.2 에 적는다. **1시간을 넘으면 넘는
# 대로 적는다** — 지키지 못하는 목표를 적어 두는 것이 가장 나쁘다.
#
# 사용:
#   scripts/restore-drill.sh <덤프파일>
#   PGHOST=... PGUSER=... PGPASSWORD=... scripts/restore-drill.sh /dumps/daily-….dump
set -euo pipefail

DUMP="${1:-}"
if [[ -z "$DUMP" || ! -f "$DUMP" ]]; then
  echo "덤프 파일을 주세요: $0 <덤프파일>" >&2
  exit 2
fi

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-15432}"
export PGUSER="${PGUSER:-codekr}"
SOURCE_DB="${PGDATABASE:-codekr}"
DRILL_DB="codekr_restore_drill_$(date -u +%s)"

# **연습이 원본을 건드리지 않는다.** 복원은 되돌릴 수 없는 작업이고, 연습이 그것을
# 실제로 하면 연습이 사고가 된다.
cleanup() {
  psql -d postgres -q -c "DROP DATABASE IF EXISTS \"$DRILL_DB\"" || true
}
trap cleanup EXIT

echo "== 연습용 데이터베이스: $DRILL_DB"
psql -d postgres -q -c "CREATE DATABASE \"$DRILL_DB\""

echo "== 복원 시작 ($(date -u +%H:%M:%S)Z)"
started=$(date +%s)
# 운영 절차와 **같은 명령**을 쓴다 (docs/09 §5.2). 다른 명령으로 잰 시간은 그 절차의
# 시간이 아니다.
pg_restore --clean --if-exists --no-owner --no-privileges -d "$DRILL_DB" "$DUMP"
elapsed=$(( $(date +%s) - started ))

tables=$(psql -d "$DRILL_DB" -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")

echo "== 걸린 시간: ${elapsed}초 (표 ${tables}개)"

# 행 수를 원본과 견준다. 복원이 끝났다는 것과 **제대로 들어갔다는 것**은 다르다.
for table in users problems submissions posts; do
  restored=$(psql -d "$DRILL_DB" -tAc "SELECT count(*) FROM $table" 2>/dev/null || echo "없음")
  original=$(psql -d "$SOURCE_DB" -tAc "SELECT count(*) FROM $table" 2>/dev/null || echo "없음")
  printf '%-12s 복원 %-10s 원본 %s\n' "$table" "$restored" "$original"
done

cat <<NOTE

이 숫자를 docs/09 §5.2 "얼마나 걸리는가" 에 적으세요.
**여기에 사람이 알아채는 시간은 들어 있지 않습니다** — 새벽에 죽었을 때 복원이
${elapsed}초여도 알아채는 데 여섯 시간이 걸리면 RTO 는 여섯 시간입니다.
NOTE
