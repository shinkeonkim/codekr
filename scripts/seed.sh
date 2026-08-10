#!/usr/bin/env bash
# 데모 계정과 시드 문제를 주입한다. 여러 번 실행해도 안전하다.
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
  curl -s -o /dev/null -X POST "${API}/api/v1/auth/signup" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\",\"nickname\":\"$3\"}" || true
}

login_token() {
  curl -s -X POST "${API}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'
}

# 어드민 권한은 API 로 올릴 수 없다 — 가입 후 DB 에서 한 번만 승격한다.
promote_admin() {
  docker exec codekr-postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
    -c "UPDATE users SET role='ADMIN' WHERE email='${ADMIN_EMAIL}';" > /dev/null
}

create_problem() {
  local token="$1" file="$2"
  local status
  status=$(curl -s -o /tmp/codekr-seed-response.json -w '%{http_code}' \
    -X POST "${API}/api/v1/admin/problems" \
    -H "Authorization: Bearer ${token}" \
    -H 'Content-Type: application/json' \
    --data-binary "@${file}")

  case "${status}" in
    201) echo "  생성됨: $(basename "${file}")" ;;
    409) echo "  이미 있음: $(basename "${file}")" ;;
    *) echo "  실패(${status}): $(basename "${file}") — $(cat /tmp/codekr-seed-response.json)" >&2 ;;
  esac
}

wait_for_api

log "데모 계정 준비"
signup "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}" "관리자"
signup "${USER_EMAIL}" "${USER_PASSWORD}" "코더"
promote_admin

TOKEN=$(login_token "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}")

log "시드 문제 주입"
for problem in "${ROOT_DIR}"/scripts/seed-problems/*.json; do
  create_problem "${TOKEN}" "${problem}"
done

log "완료"
echo "  어드민: ${ADMIN_EMAIL} / ${ADMIN_PASSWORD}"
echo "  사용자: ${USER_EMAIL} / ${USER_PASSWORD}"
echo "  사이트: http://localhost:${WEB_HOST_PORT:-13000}"
