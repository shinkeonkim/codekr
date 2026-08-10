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
TOKEN=$(curl -s -X POST "${API}/api/v1/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"nickname\":\"스모크$(date +%s)\"}" \
  | json '["accessToken"]') || fail "회원가입 실패"
pass "가입 및 토큰 발급"

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

submit() {
  curl -s -X POST "${API}/api/v1/problems/two-sum/submissions" \
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"runtimeId\":\"python:3.12\",\"sourceCode\":\"$1\"}" | json '["submissionId"]'
}

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

submit_to() {
  curl -s -X POST "${API}/api/v1/problems/$1/submissions" \
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"runtimeId\":\"python:3.12\",\"sourceCode\":\"${SLEEPY}\"}" | json '["submissionId"]'
}

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

submit_with_runtime() {
  curl -s -X POST "${API}/api/v1/problems/${PER_LANG_SLUG}/submissions" \
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"runtimeId\":\"$1\",\"sourceCode\":\"${SLEEPY}\"}" | json '["submissionId"]'
}

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
LOW_IDS=()
for _ in $(seq 1 16); do
  body=$(curl -s -X POST "${API}/api/v1/problems/${PRIO_SLUG}/submissions" \
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"runtimeId\":\"python:3.12\",\"sourceCode\":\"${SLOW}\"}")
  id=$(echo "${body}" | json '["submissionId"]' 2>/dev/null) \
    || fail "낮은 등급 제출에 실패했습니다: ${body}"
  LOW_IDS+=("${id}")
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
