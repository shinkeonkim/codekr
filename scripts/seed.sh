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

# 첫 최고 관리자는 API 로 만들 수 없다 — 역할을 줄 수 있는 사람이 아직 없다.
# 그래서 가입 후 DB 에서 한 번만 올린다. 이후의 역할 부여는 API 로 한다 (#103).
promote_admin() {
  docker exec codekr-postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
    -c "INSERT INTO user_roles (user_id, role)
        SELECT id, 'SUPERUSER' FROM users WHERE email='${ADMIN_EMAIL}'
        ON CONFLICT DO NOTHING;" > /dev/null
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

# 알고리즘 분류를 만들고 시드 문제에 붙인다 (#232).
#
# **비어 있는 채로 시작하면 필터가 아무것도 걸러 주지 못한다.** 태그 기능만 있고 붙은
# 문제가 없으면, 처음 켠 사람은 그것이 고장 난 것인지 원래 그런 것인지 알 수 없다.
#
# 이미 있는 태그·이미 붙은 문제는 그대로 둔다 — 시드는 여러 번 돌 수 있어야 한다.
seed_tags() {
  local token="$1"
  API="${API}" TOKEN="${token}" python3 - "${ROOT_DIR}/scripts/seed-tags.json" <<'PYTHON'
import json
import os
import sys
import urllib.error
import urllib.request

api, token = os.environ["API"], os.environ["TOKEN"]
seed = json.load(open(sys.argv[1], encoding="utf-8"))


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(f"{api}{path}", data=data, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    if data:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read() or "null")
    except urllib.error.HTTPError as error:
        # 이미 있는 태그는 400, 없는 문제는 404 로 돌아온다. 둘 다 넘어간다.
        if error.code in (400, 404):
            return None
        raise


for tag in seed["tags"]:
    call("POST", "/api/v1/admin/tags", tag)

by_slug = {tag["slug"]: tag["id"] for tag in call("GET", "/api/v1/tags")}
print(f"  태그 {len(by_slug)}개")

for problem_slug, tag_slugs in seed["problems"].items():
    problem = call("GET", f"/api/v1/problems/{problem_slug}")
    if problem is None:
        print(f"  건너뜀(문제 없음): {problem_slug}")
        continue
    call(
        "PUT",
        f"/api/v1/admin/problems/{problem['id']}/tags",
        {"tagIds": [by_slug[slug] for slug in tag_slugs if slug in by_slug]},
    )
    print(f"  {problem_slug}: {', '.join(tag_slugs)}")
PYTHON
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

log "알고리즘 분류 주입"
seed_tags "${TOKEN}"

log "완료"
echo "  어드민: ${ADMIN_EMAIL} / ${ADMIN_PASSWORD}"
echo "  사용자: ${USER_EMAIL} / ${USER_PASSWORD}"
echo "  사이트: http://localhost:${WEB_HOST_PORT:-13000}"
