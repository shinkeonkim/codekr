#!/usr/bin/env bash
# 로컬 개발용 시드 (#15, #342). 데모 계정을 만들고 시드 문제를 넣는다.
#
# **여기는 로컬 전용이다.** 데모 계정을 만들고 DB 에 직접 붙기 때문이다.
# 문제를 넣는 일 자체는 `seed-problems.sh` 가 하고, 그쪽은 운영에서도 쓴다 —
# **운영에 데모 계정이 만들어지는 길을 옵션이 아니라 구조로 막는다.**
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
[[ -f "${ROOT_DIR}/.env" ]] && source "${ROOT_DIR}/.env"

API="http://localhost:${API_HOST_PORT:-18080}"
ADMIN_EMAIL="${SEED_ADMIN_EMAIL:-admin@codekr.dev}"
ADMIN_PASSWORD="${SEED_ADMIN_PASSWORD:-admin1234}"
USER_EMAIL="${SEED_USER_EMAIL:-user@codekr.dev}"
USER_PASSWORD="${SEED_USER_PASSWORD:-user1234}"
POSTGRES_USER="${POSTGRES_USER:-codekr}"
POSTGRES_DB="${POSTGRES_DB:-codekr}"

log() { printf '\033[36m==>\033[0m %s\n' "$1"; }

wait_for_api() {
  log "API 기동 대기 (${API})"
  for _ in $(seq 1 60); do
    if curl -sf "${API}/actuator/health" > /dev/null; then return 0; fi
    sleep 2
  done
  echo "API 가 준비되지 않았습니다. 'make logs-api' 로 로그를 확인하세요." >&2
  exit 1
}

# 이미 가입된 계정이면 409 가 나므로 실패를 무시하고 로그인으로 넘어간다.
signup() {
  # 필수 약관에 동의해야 가입이 된다 (#235). 시행 중인 판을 그때그때 읽어 넣는다.
  local ids
  ids=$(curl -s "${API}/api/v1/terms" | python3 -c 'import json,sys; print([t["id"] for t in json.load(sys.stdin)])')
  curl -s -o /dev/null -X POST "${API}/api/v1/auth/signup" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\",\"nickname\":\"$3\",\"agreedTermIds\":${ids}}" || true
}

login_token() {
  curl -s -X POST "${API}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'
}

# 첫 최고 관리자는 API 로 만들 수 없다 — 역할을 줄 수 있는 사람이 아직 없다.
# 그래서 가입 후 DB 에서 한 번만 올린다. 이후의 역할 부여는 API 로 한다 (#103).
#
# **운영에는 이미 어드민이 있으므로 이 단계가 필요 없다.** 그것이 이 스크립트가
# 로컬 전용인 두 번째 이유다.
promote_admin() {
  docker exec codekr-postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
    -c "INSERT INTO user_roles (user_id, role)
        SELECT id, 'SUPERUSER' FROM users WHERE email='${ADMIN_EMAIL}'
        ON CONFLICT DO NOTHING;" > /dev/null
}

wait_for_api

log "데모 계정 준비"
signup "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}" "관리자"
signup "${USER_EMAIL}" "${USER_PASSWORD}" "코더"
promote_admin

# 토큰을 인자로 넘기지 않는다 — `ps` 에 보이고 히스토리에 남는다.
CODEKR_API="${API}" CODEKR_ADMIN_TOKEN="$(login_token "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}")" \
  "${ROOT_DIR}/scripts/seed-problems.sh"

echo "  어드민: ${ADMIN_EMAIL} / ${ADMIN_PASSWORD}"
echo "  사용자: ${USER_EMAIL} / ${USER_PASSWORD}"
echo "  사이트: http://localhost:${WEB_HOST_PORT:-13000}"
