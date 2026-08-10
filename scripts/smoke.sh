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
  for _ in $(seq 1 60); do
    local body status
    body=$(curl -s "${API}/api/v1/submissions/${submission_id}" -H "Authorization: Bearer ${TOKEN}")
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

printf '\n\033[32m스모크 테스트 통과\033[0m — 채점 파이프라인이 정상 동작합니다.\n'
