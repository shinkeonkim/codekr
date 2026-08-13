#!/usr/bin/env bash
# 이번 실행에서 어느 영역을 돌릴지 정한다 (#317, #343).
#
# **워크플로 YAML 이 아니라 파일이다.** 이 저장소가 CI 에서 겪은 사고는 전부 같은
# 종류였다 — #248 은 조용히 건너뛰어진 잡, #172 는 돌지 않았는데 초록. 그 판정이
# YAML 안의 인라인 셸에 있으면 **고치는 사람이 확인할 방법이 푸시뿐**이다.
# 파일로 두면 손으로 돌려 볼 수 있다:
#
#   EVENT_NAME=workflow_dispatch PICK_API=true .github/scripts/decide-scope.sh
#
# 규칙: **고른 영역 ∪ (변경된 것만 을 켰다면 변경된 영역).**
# 겹치는 조합이 아니라 뜻이 있는 조합이다 — "변경된 것을 돌리되 api 는 변경이
# 없어도 돌려라".
set -eu

EVENT_NAME="${EVENT_NAME:-push}"
CHANGED_ONLY="${CHANGED_ONLY:-true}"
OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"

selected=""
for area in api web go sandbox; do
  upper=$(echo "$area" | tr '[:lower:]' '[:upper:]')

  eval "picked=\${PICK_${upper}:-false}"
  # 건너뛴 paths-filter 의 출력은 빈 문자열이다 — false 로 읽는다.
  eval "changed=\${CHANGED_${upper}:-false}"

  value=false
  [ "$picked" = "true" ] && value=true
  [ "$CHANGED_ONLY" = "true" ] && [ "$changed" = "true" ] && value=true

  echo "$area=$value" >> "$OUTPUT"
  [ "$value" = "true" ] && selected="${selected}${area} "
done

echo "돌릴 영역: ${selected:-(없음)}" >&2

# 손으로 돌린 실행이 아무것도 안 돌고 초록이 되지 않게 한다 (#343).
#
# **자동 실행에는 이 규칙을 걸지 않는다.** 문서만 고친 PR 은 아무것도 안 도는 것이
# 정상이고, 그것을 빨갛게 만들면 사람이 빨강을 무시하기 시작한다.
if [ -z "$selected" ] && [ "$EVENT_NAME" = "workflow_dispatch" ]; then
  if [ "$CHANGED_ONLY" = "true" ]; then
    echo "::error::돌릴 것이 없습니다 — 기본 브랜치와 달라진 영역이 없고 고른 영역도 없습니다." >&2
  else
    echo "::error::돌릴 것이 없습니다 — 영역을 하나도 고르지 않았습니다." >&2
  fi
  exit 1
fi
