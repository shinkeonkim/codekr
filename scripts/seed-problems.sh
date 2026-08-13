#!/usr/bin/env bash
# 저장소의 시드 문제와 알고리즘 분류를 API 에 넣는다 (#342).
#
# **계정을 만들지 않는다.** 그것이 이 스크립트가 따로 있는 이유다 — 운영에 데모 계정이
# 만들어지는 길을 옵션으로 막는 것이 아니라 **구조로** 막는다. 옵션은 켜지고, 구조는
# 켜지지 않는다.
#
# 로컬은 `make seed` 가 계정을 준비한 뒤 이것을 부른다. 운영은 사람이 직접 부른다.
#
#   CODEKR_API=https://xn--hy1by51c.kr CODEKR_ADMIN_TOKEN=... scripts/seed-problems.sh
#   scripts/seed-problems.sh --dry-run          # 무엇이 들어가는지만 본다
#
# **토큰을 인자로 받지 않는다.** 셸 히스토리에 남기 때문이다. 환경 변수나
# `--token-file` 로만 받는다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

API="${CODEKR_API:-}"
TOKEN="${CODEKR_ADMIN_TOKEN:-}"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api) API="$2"; shift 2 ;;
    --token-file) TOKEN="$(cat "$2")"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    *) echo "모르는 인자: $1" >&2; exit 2 ;;
  esac
done

log() { printf '\033[36m==>\033[0m %s\n' "$1"; }

# **넣는 순서가 곧 문제 번호다** (#204). 글로브 순서에 기대지 않고 이름으로 정렬한다 —
# 로케일에 따라 글로브 결과가 달라지면 같은 저장소가 다른 번호를 만든다.
problem_files() {
  find "${ROOT_DIR}/scripts/seed-problems" -maxdepth 1 -name '*.json' | LC_ALL=C sort
}

# SQL 문제는 스키마를 **별도 파일**에서 끼워 넣는다 (#313).
#
# 다섯 문제가 같은 스키마를 쓰는데 `schema_sql` 은 문제마다 저장된다. 시드 JSON 안에
# SQL 을 통째로 박으면 한 글자를 고칠 때 다섯 파일을 고쳐야 하고, JSON 문자열 안의
# 여러 줄 SQL 은 읽을 수가 없다.
#
# **조립은 보내기 전에 끝난다** — `sqlSpec` 은 어드민 API 의 규격이라 필드 이름을 바꿀
# 수 없다. `sqlSchemaFile` 은 시드에서만 쓰는 키이고 여기서 사라진다.
assemble_problem() {
  SEED_DIR="$(dirname "$1")" python3 - "$1" <<'PYTHON'
import json
import os
import sys

body = json.load(open(sys.argv[1], encoding="utf-8"))
schema_file = body.pop("sqlSchemaFile", None)
if schema_file:
    path = os.path.join(os.environ["SEED_DIR"], schema_file)
    body.setdefault("sqlSpec", {})["schemaSql"] = open(path, encoding="utf-8").read()
json.dump(body, sys.stdout, ensure_ascii=False)
PYTHON
}

# 넣기 전에 무엇이 들어가는지 보여준다. 12개를 한 번에 밀어 넣는 일이다.
show_plan() {
  log "들어갈 문제 (순서대로)"
  local index=0
  while IFS= read -r file; do
    index=$((index + 1))
    INDEX="${index}" python3 - "${file}" <<'PYTHON'
import json
import os
import sys

body = json.load(open(sys.argv[1], encoding="utf-8"))
order = int(os.environ["INDEX"])
print(f"  {order:2d}. {body['slug']:28s} {body.get('category', '?'):10s} {body['title']}")
PYTHON
  done < <(problem_files)
  log "받는 곳: ${API}"
}

create_problem() {
  local file="$1" status
  status=$(assemble_problem "${file}" | curl -s -o /tmp/codekr-seed-response.json -w '%{http_code}' \
    -X POST "${API}/api/v1/admin/problems" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary @-)

  case "${status}" in
    # **이미 있으면 건너뛴다 — 덮어쓰지 않는다.** 운영의 문제는 손으로 고쳐졌을 수
    # 있고, 시드가 그것을 되돌리면 고친 사람은 이유를 알 수 없다.
    201) echo "  생성됨: $(basename "${file}")" ;;
    409) echo "  이미 있음: $(basename "${file}")" ;;
    *) echo "  실패(${status}): $(basename "${file}") — $(cat /tmp/codekr-seed-response.json)" >&2; return 1 ;;
  esac
}

# 알고리즘 분류를 만들고 시드 문제에 붙인다 (#232).
#
# **비어 있는 채로 시작하면 필터가 아무것도 걸러 주지 못한다.** 태그 기능만 있고 붙은
# 문제가 없으면, 처음 켠 사람은 그것이 고장 난 것인지 원래 그런 것인지 알 수 없다.
seed_tags() {
  API="${API}" TOKEN="${TOKEN}" python3 - "${ROOT_DIR}/scripts/seed-tags.json" <<'PYTHON'
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

[[ -n "${API}" ]] || { echo "API 주소가 없습니다. CODEKR_API 또는 --api 로 주세요." >&2; exit 2; }

show_plan

if [[ "${DRY_RUN}" == 1 ]]; then
  log "--dry-run 이라 여기서 멈춥니다. 아무것도 보내지 않았습니다."
  exit 0
fi

[[ -n "${TOKEN}" ]] || {
  echo "어드민 토큰이 없습니다. CODEKR_ADMIN_TOKEN 또는 --token-file 로 주세요." >&2
  echo "(인자로는 받지 않습니다 — 셸 히스토리에 남습니다.)" >&2
  exit 2
}

log "시드 문제 주입"
while IFS= read -r problem; do
  create_problem "${problem}"
done < <(problem_files)

log "알고리즘 분류 주입"
seed_tags

log "완료"
