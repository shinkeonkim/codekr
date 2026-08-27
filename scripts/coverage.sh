#!/usr/bin/env bash
# 테스트 커버리지를 재고 한곳에 모은다 (#642).
#
# **세 스택이 각자 다른 도구를 쓴다** — api 는 JaCoCo, web 은 bun, go 는 `go tool cover`.
# 그것을 바꾸려 하지 않는다. 바꾸면 각 스택의 표준에서 벗어나고, 얻는 것은 통일감뿐이다.
# 대신 **나온 것을 한 폴더에 모으고 요약을 한 장으로 만든다** — 사람이 보는 곳이
# 하나면 된다.
#
#   bash scripts/coverage.sh            # 셋 다
#   bash scripts/coverage.sh api        # 하나만 (api | web | go)
#   SKIP_INTEGRATION=1 bash scripts/coverage.sh api   # Testcontainers 없이
#   GO_MODULES=apps/executor bash scripts/coverage.sh go   # go 모듈 하나만
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ROOT="$(pwd)"
OUT="${ROOT}/build/coverage"
WHAT="${1:-all}"

step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }
fail=0
mkdir -p "${OUT}"

want() { [[ "${WHAT}" == "all" || "${WHAT}" == "$1" ]]; }

# ── api ──────────────────────────────────────────────────────────────────────
# **단위와 통합을 합쳐서 본다.** 나누면 통합 시험이 덮은 코드가 단위 쪽 리포트에서
# 구멍으로 보이고, 그 숫자를 보고 있으면 이미 시험된 것에 시험을 또 쓰게 된다.
# 시험 파일 114개 중 95개가 통합 시험이라 이 구분은 사소하지 않다.
if want api; then
  step "api — JaCoCo (단위 + 통합)"
  tasks=(test coverageReport)
  if [[ "${SKIP_INTEGRATION:-0}" != "1" ]]; then
    tasks=(test integrationTest coverageReport)
  else
    # 합치지 않고 재면 숫자가 실제보다 훨씬 낮다. 말하지 않으면 그것을 그대로 믿는다.
    echo "  · 통합 시험을 건너뜁니다 — 이 숫자는 단위 시험만의 것입니다"
    rm -f "${ROOT}/apps/api/build/jacoco/integrationTest.exec"
  fi
  (cd apps/api && ./gradlew "${tasks[@]}" --console=plain) || fail=1

  report="${ROOT}/apps/api/build/reports/jacoco/coverageReport"
  # **리포트가 실제로 생겼는지 본다.** JaCoCo 는 기록이 없으면 태스크를 조용히
  # SKIPPED 하고 빌드는 초록이다 — 이 저장소가 두 번 겪은 모양이다 (#172, #248).
  if [[ -f "${report}/html/index.html" ]]; then
    rm -rf "${OUT}/api" && mkdir -p "${OUT}/api"
    cp -R "${report}/html/." "${OUT}/api/"
    cp "${report}/coverageReport.xml" "${OUT}/api.xml"
  else
    echo "✗ api 리포트가 생기지 않았습니다 (${report})"
    fail=1
  fi
fi

# ── web ──────────────────────────────────────────────────────────────────────
if want web; then
  step "web — bun"
  rm -rf "${OUT}/web" && mkdir -p "${OUT}/web"
  # 표는 사람이 읽고, lcov 는 편집기가 읽는다 (VS Code 의 Coverage Gutters 등).
  # **HTML 은 만들지 않는다** — lcov 를 HTML 로 바꾸려면 `genhtml`(lcov 꾸러미)이
  # 있어야 하고, 커버리지를 보려고 시스템 의존성을 하나 더 요구하게 된다.
  (cd apps/web && bun test --coverage \
    --coverage-reporter=text --coverage-reporter=lcov \
    --coverage-dir="${OUT}/web" 2>&1 | tee "${OUT}/web/summary.txt") || fail=1
  [[ -f "${OUT}/web/lcov.info" ]] || { echo "✗ web lcov 가 생기지 않았습니다"; fail=1; }
fi

# ── go ───────────────────────────────────────────────────────────────────────
if want go; then
  step "go — executor · judge · gocontract"
  rm -rf "${OUT}/go" && mkdir -p "${OUT}/go"
  # **모듈을 경로로 받는다** (#668). 전에는 이름만 받고 여기서 `apps/` 를 붙였는데,
  # 그래서 `apps/` 밑이 아닌 `libs/gocontract` 를 부를 방법이 없었다 — Makefile 은
  # 경로로 적고 있어 두 곳이 다른 말을 쓰고 있었다.
  #
  # CI 는 모듈마다 잡이 따로라 하나씩 부른다: `GO_MODULES=apps/executor bash scripts/coverage.sh go`
  for module in ${GO_MODULES:-apps/executor apps/judge libs/gocontract}; do
    name="$(basename "${module}")"
    # **`-coverpkg=./...` 가 있어야 한다.** 없으면 시험 파일이 없는 꾸러미
    # (`cmd/`·`config`·`httpapi`)가 프로파일에 **아예 안 들어가고**, 0% 가 아니라
    # 없는 것이 되어 전체 비율이 실제보다 높게 나온다.
    (cd "${module}" && go test ./... -covermode=set \
      -coverpkg=./... -coverprofile="${OUT}/go/${name}.out") || fail=1
    if [[ -f "${OUT}/go/${name}.out" ]]; then
      (cd "${module}" && go tool cover -html="${OUT}/go/${name}.out" -o "${OUT}/go/${name}.html") || fail=1
    fi
  done
fi

# ── 요약 ─────────────────────────────────────────────────────────────────────
step "요약"
python3 scripts/coverage-summary.py "${OUT}" || fail=1

echo
echo "리포트: ${OUT}/index.html"
echo "  api  ${OUT}/api/index.html    (줄 단위로 보인다)"
echo "  web  ${OUT}/web/summary.txt   (lcov.info 는 편집기가 읽는다)"
# 손으로 적으면 모듈이 늘 때 또 빠뜨린다 (#668). 만들어진 것을 그대로 읽는다.
echo "  go   $(ls "${OUT}"/go/*.html 2>/dev/null | tr '\n' ' ')"

if [[ "${fail}" -ne 0 ]]; then
  printf '\n\033[31m✗ 실패한 항목이 있습니다.\033[0m\n'
  exit 1
fi
