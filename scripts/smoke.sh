#!/usr/bin/env bash
# 로컬 스택 E2E 스모크: 가입 → 로그인 → 문제 조회 → 실행 → 제출 → 판정 확인.
# 채점 파이프라인(API → 채점 큐 → 채점기 → 실행 큐 → 실행기)이 실제로 도는지 검증한다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
[[ -f "${ROOT_DIR}/.env" ]] && source "${ROOT_DIR}/.env"

API="http://localhost:${API_HOST_PORT:-18080}"
EMAIL="smoke-$(date +%s)@codekr.dev"
PASSWORD="smoke1234"
SOLUTION='import sys\nprint(sum(map(int, sys.stdin.read().split())))'
WRONG_SOLUTION='print(0)'

pass() { printf '\033[32m  ✓\033[0m %s\n' "$1"; }
fail() { printf '\033[31m  ✗\033[0m %s\n' "$1"; exit 1; }
step() { printf '\033[36m==>\033[0m %s\n' "$1"; }

json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

step "1. 회원가입"
# **필수 약관 동의가 가입에 필요하다** (#235). id 를 박아 두지 않고 그때그때 읽는다 —
# 시드가 바뀌면 번호도 바뀌고, 박아 두면 이 스크립트가 또 조용히 깨진다.
AGREED=$(curl -s "${API}/api/v1/terms" \
  | python3 -c "import json,sys; print(json.dumps([t['id'] for t in json.load(sys.stdin) if t['required']]))") \
  || fail "약관 목록을 읽지 못했습니다"
SIGNUP=$(curl -s -X POST "${API}/api/v1/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"nickname\":\"스모크$(date +%s)\",\"agreedTermIds\":${AGREED}}")
# **실패하면 서버가 말한 이유를 그대로 보인다.** 예전에는 `KeyError: 'accessToken'` 만
# 나와서, 무엇이 모자란지 응답을 직접 열어 보기 전에는 알 수 없었다.
TOKEN=$(echo "${SIGNUP}" | json '["accessToken"]' 2>/dev/null) || fail "회원가입 실패: ${SIGNUP}"
pass "가입 및 토큰 발급 (동의한 필수 약관 ${AGREED})"

step "2. 문제 목록 조회"
COUNT=$(curl -s "${API}/api/v1/problems" | json '["totalElements"]')
[[ "${COUNT}" -gt 0 ]] || fail "공개된 문제가 없습니다. 'make seed' 를 먼저 실행하세요."
pass "공개 문제 ${COUNT}건"

step "3. 문제 상세 조회 (히든 테스트케이스 비노출 확인)"
DETAIL=$(curl -s "${API}/api/v1/problems/two-sum")
echo "${DETAIL}" | grep -q 'expectedOutput' && fail "상세 응답에 기대 출력이 노출되었습니다"
pass "히든 테스트케이스 비노출"

step "4. 코드 실행"
RUN_STDOUT=$(curl -s -X POST "${API}/api/v1/problems/two-sum/run" \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d "{\"runtimeId\":\"python:3.12\",\"sourceCode\":\"${SOLUTION}\",\"stdin\":\"7 8\\n\"}" \
  | json '["stdout"]')
[[ "${RUN_STDOUT}" == "15" ]] || fail "실행 결과가 기대와 다릅니다: ${RUN_STDOUT}"
pass "실행 결과 15"

await_verdict() {
  local submission_id="$1"
  # 두 번째 인자로 토큰을 받는다. 어드민 검증 제출은 사용자 토큰으로 조회할 수 없다.
  local token="${2:-${TOKEN}}"
  for _ in $(seq 1 60); do
    local body status
    body=$(curl -s "${API}/api/v1/submissions/${submission_id}" -H "Authorization: Bearer ${token}")
    status=$(echo "${body}" | json '["status"]')
    if [[ "${status}" == "COMPLETED" || "${status}" == "FAILED" ]]; then
      echo "${body}" | json '["verdict"]'
      return 0
    fi
    sleep 1
  done
  fail "채점이 제한 시간 안에 끝나지 않았습니다"
}

# **같은 사람이 같은 문제를 30초에 한 번만 낼 수 있다** (#189). 이 스크립트는 한 문제에
# 여러 번 내므로 그 제한에 걸린다 — 서버가 옳고 스크립트가 기다려야 한다.
#
# 서버가 남은 초를 알려 주므로 그것을 읽어 그만큼 자고 다시 낸다. 고정된 초를 자면
# 제한이 바뀔 때 또 깨진다.
#
# 제출하는 곳이 넷이라 **한 곳에 모았다.** 따로 쓰면 이번처럼 한 곳만 고쳐진다.
post_submission() {
  local slug="$1" runtime="$2" source="$3" body remaining
  for _ in 1 2; do
    body=$(curl -s -X POST "${API}/api/v1/problems/${slug}/submissions" \
      -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
      -d "{\"runtimeId\":\"${runtime}\",\"sourceCode\":\"${source}\"}")
    if echo "${body}" | grep -q '"submissionId"'; then
      echo "${body}" | json '["submissionId"]'
      return 0
    fi
    echo "${body}" | grep -q 'SUBMISSION_TOO_FREQUENT' || fail "제출 실패(${slug}): ${body}"
    remaining=$(echo "${body}" | python3 -c "import json,re,sys; print(re.search(r'(\d+)초 뒤', json.load(sys.stdin)['message']).group(1))")
    printf '     제출 간격 제한 — %s초 기다립니다\n' "${remaining}" >&2
    sleep "$((remaining + 1))"
  done
  fail "제출 간격 제한이 풀리지 않았습니다: ${slug}"
}

submit() { post_submission "two-sum" "python:3.12" "$1"; }

step "5. 정답 제출"
VERDICT=$(await_verdict "$(submit "${SOLUTION}")")
[[ "${VERDICT}" == "ACCEPTED" ]] || fail "정답이 ACCEPTED 로 채점되지 않았습니다: ${VERDICT}"
pass "ACCEPTED"

step "6. 오답 제출"
VERDICT=$(await_verdict "$(submit "${WRONG_SOLUTION}")")
[[ "${VERDICT}" == "WRONG_ANSWER" ]] || fail "오답이 WRONG_ANSWER 로 채점되지 않았습니다: ${VERDICT}"
pass "WRONG_ANSWER"

step "7. 무한 루프 제출"
VERDICT=$(await_verdict "$(submit 'while True: pass')")
[[ "${VERDICT}" == "TIME_LIMIT_EXCEEDED" ]] || fail "시간 초과로 채점되지 않았습니다: ${VERDICT}"
pass "TIME_LIMIT_EXCEEDED"

step "8. 문제별 시간 제한이 실제로 강제되는지"
# 같은 코드(1.5초 대기)를 제한만 다른 두 문제에 제출해, 문제 설정이 실제 판정을 가르는지 본다.
SLEEPY="import time\ntime.sleep(1.5)\nprint('done')"
ADMIN_TOKEN=$(curl -s -X POST "${API}/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${SEED_ADMIN_EMAIL:-admin@codekr.dev}\",\"password\":\"${SEED_ADMIN_PASSWORD:-admin1234}\"}" \
  | json '["accessToken"]') || fail "어드민 로그인 실패 (make seed 를 먼저 실행하세요)"

create_timed_problem() {
  local slug="$1" limit="$2" status
  status=$(curl -s -o /tmp/codekr-smoke-problem.json -w '%{http_code}' -X POST "${API}/api/v1/admin/problems" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"slug\":\"${slug}\",\"title\":\"제한 검증 ${limit}ms\",\"category\":\"ALGORITHM\",
         \"difficulty\":\"BRONZE_5\",\"description\":\"스모크 전용\",\"timeLimitMs\":${limit},
         \"memoryLimitMb\":256,\"published\":true,
         \"testcases\":[{\"seq\":1,\"input\":\"\",\"expectedOutput\":\"done\\n\",\"visibility\":\"HIDDEN\"}]}")
  [[ "${status}" == "201" ]] || fail "검증용 문제 생성 실패(${status}): $(cat /tmp/codekr-smoke-problem.json)"
}

SUFFIX=$(date +%s)
create_timed_problem "limit-generous-${SUFFIX}" 5000
create_timed_problem "limit-tight-${SUFFIX}" 500

submit_to() { post_submission "$1" "python:3.12" "${SLEEPY}"; }

VERDICT=$(await_verdict "$(submit_to "limit-generous-${SUFFIX}")")
[[ "${VERDICT}" == "ACCEPTED" ]] || fail "여유 있는 제한(5000ms)에서 통과해야 합니다: ${VERDICT}"
pass "5000ms 제한 → ACCEPTED"

VERDICT=$(await_verdict "$(submit_to "limit-tight-${SUFFIX}")")
[[ "${VERDICT}" == "TIME_LIMIT_EXCEEDED" ]] || fail "빠듯한 제한(500ms)에서 시간 초과여야 합니다: ${VERDICT}"
pass "500ms 제한 → TIME_LIMIT_EXCEEDED (같은 코드)"

step "9. 같은 문제에서 언어별 제한이 다르게 적용되는지"
# 한 문제에 python:3.12 만 넉넉한 제한을 준다. 같은 1.5초 코드를 두 런타임으로 제출해
# 언어별 오버라이드가 실제 판정을 가르는지 본다 (#97).
PER_LANG_SLUG="limit-per-lang-${SUFFIX}"
status=$(curl -s -o /tmp/codekr-smoke-problem.json -w '%{http_code}' -X POST "${API}/api/v1/admin/problems" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' \
  -d "{\"slug\":\"${PER_LANG_SLUG}\",\"title\":\"언어별 제한 검증\",\"category\":\"ALGORITHM\",
       \"difficulty\":\"BRONZE_5\",\"description\":\"스모크 전용\",\"timeLimitMs\":500,
       \"memoryLimitMb\":256,\"published\":true,
       \"testcases\":[{\"seq\":1,\"input\":\"\",\"expectedOutput\":\"done\\n\",\"visibility\":\"HIDDEN\"}],
       \"runtimeLimits\":[{\"runtimeId\":\"python:3.12\",\"timeLimitMs\":5000,\"memoryLimitMb\":256}]}")
[[ "${status}" == "201" ]] || fail "언어별 제한 문제 생성 실패(${status}): $(cat /tmp/codekr-smoke-problem.json)"

submit_with_runtime() { post_submission "${PER_LANG_SLUG}" "$1" "${SLEEPY}"; }

VERDICT=$(await_verdict "$(submit_with_runtime "python:3.12")")
[[ "${VERDICT}" == "ACCEPTED" ]] || fail "오버라이드된 런타임(5000ms)에서 통과해야 합니다: ${VERDICT}"
pass "python:3.12 (오버라이드 5000ms) → ACCEPTED"

VERDICT=$(await_verdict "$(submit_with_runtime "python:3.13")")
[[ "${VERDICT}" == "TIME_LIMIT_EXCEEDED" ]] || fail "기본 제한(500ms)을 쓰는 런타임은 시간 초과여야 합니다: ${VERDICT}"
pass "python:3.13 (문제 기본 500ms) → TIME_LIMIT_EXCEEDED (같은 코드, 같은 문제)"

step "10. 채점 큐 우선순위 — 밀린 큐를 어드민 검증이 앞질러 가는지"
# 낮은 등급 제출을 잔뜩 넣어 큐를 막은 뒤, 어드민 정답 검증을 넣는다.
# 검증이 먼저 끝나야 한다 (#102).
PRIO_SLUG="prio-check-${SUFFIX}"
SLOW="import time\ntime.sleep(2)\nprint('done')"
status=$(curl -s -o /tmp/codekr-smoke-problem.json -w '%{http_code}' -X POST "${API}/api/v1/admin/problems" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' \
  -d "{\"slug\":\"${PRIO_SLUG}\",\"title\":\"우선순위 검증\",\"category\":\"ALGORITHM\",
       \"difficulty\":\"BRONZE_5\",\"description\":\"스모크 전용\",\"timeLimitMs\":5000,
       \"memoryLimitMb\":256,\"published\":true,\"judgePriority\":\"LOW\",
       \"testcases\":[{\"seq\":1,\"input\":\"\",\"expectedOutput\":\"done\\n\",\"visibility\":\"HIDDEN\"}],
       \"solution\":{\"runtimeId\":\"python:3.12\",\"sourceCode\":\"${SLOW}\"}}")
[[ "${status}" == "201" ]] || fail "우선순위 검증 문제 생성 실패(${status}): $(cat /tmp/codekr-smoke-problem.json)"
PRIO_ID=$(json '["id"]' < /tmp/codekr-smoke-problem.json)

# 낮은 등급으로 큐를 채운다.
#
# **문제를 열여섯 개 만들어 하나씩 낸다.** 한 문제에 열여섯 번 내면 제출 간격 제한
# (#189)에 걸리는데, 그 제한을 기다리며 내면 30초에 하나씩 들어가서 **큐가 막히지
# 않는다** — 막힌 큐를 앞지르는지 보는 것이 이 단계의 전부다. 제한은 사람과 문제의
# 짝마다이므로, 문제를 나누면 한 사람이 한 번에 다 낼 수 있다.
create_low_problem() {
  local slug="$1" status
  status=$(curl -s -o /tmp/codekr-smoke-problem.json -w '%{http_code}' -X POST "${API}/api/v1/admin/problems" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"slug\":\"${slug}\",\"title\":\"큐 채우기\",\"category\":\"ALGORITHM\",
         \"difficulty\":\"BRONZE_5\",\"description\":\"스모크 전용\",\"timeLimitMs\":5000,
         \"memoryLimitMb\":256,\"published\":true,\"judgePriority\":\"LOW\",
         \"testcases\":[{\"seq\":1,\"input\":\"\",\"expectedOutput\":\"done\\n\",\"visibility\":\"HIDDEN\"}]}")
  [[ "${status}" == "201" ]] || fail "큐 채우기 문제 생성 실패(${status}): $(cat /tmp/codekr-smoke-problem.json)"
}

LOW_IDS=()
for i in $(seq 1 16); do
  create_low_problem "prio-fill-${SUFFIX}-${i}"
  LOW_IDS+=("$(post_submission "prio-fill-${SUFFIX}-${i}" "python:3.12" "${SLOW}")")
done

# 그 뒤에 어드민 검증(최상위)을 넣는다.
VERIFY_ID=$(curl -s -X POST "${API}/api/v1/admin/problems/${PRIO_ID}/verify" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | json '["submissionId"]')
[[ -n "${VERIFY_ID}" ]] || fail "정답 검증을 시작하지 못했습니다"

VERDICT=$(await_verdict "${VERIFY_ID}" "${ADMIN_TOKEN}")
[[ "${VERDICT}" == "ACCEPTED" ]] || fail "정답 검증이 통과해야 합니다: ${VERDICT}"

# 완료만으로는 '앞질렀다'를 증명하지 못한다 — 낮은 등급이 그때까지 다 끝났을 수도 있다.
# 검증이 끝난 시점에 낮은 등급이 아직 남아 있어야 실제로 새치기한 것이다.
REMAINING=0
for id in "${LOW_IDS[@]}"; do
  st=$(curl -s "${API}/api/v1/submissions/${id}" -H "Authorization: Bearer ${TOKEN}" | json '["status"]')
  [[ "${st}" == "COMPLETED" || "${st}" == "FAILED" ]] || REMAINING=$((REMAINING + 1))
done
[[ "${REMAINING}" -gt 0 ]] || fail "낮은 등급이 모두 끝난 뒤라 우선순위를 확인할 수 없습니다"
pass "낮은 등급 ${REMAINING}건이 남은 상태에서 어드민 검증이 먼저 완료 (${VERDICT})"

printf '\n\033[32m스모크 테스트 통과\033[0m — 채점 파이프라인이 정상 동작합니다.\n'
