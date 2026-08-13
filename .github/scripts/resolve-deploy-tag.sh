#!/usr/bin/env bash
# 손으로 배포할 때 어느 커밋을 적을지 정한다 (#348).
#
# **파일로 둔 이유는 #343 과 같다** — 배포 판정을 YAML 안의 인라인 셸에 두면 고치는
# 사람이 확인할 방법이 "돌려 보는 것" 뿐인데, 여기서 돌려 본다는 것은 **운영에 배포해
# 본다는 뜻**이다.
#
#   REQUESTED=<sha 또는 빈 값> .github/scripts/resolve-deploy-tag.sh
#
# 빈 값이면 **직전 배포로 되돌린다** — 가장 흔한 경우라 아무것도 안 적어도 되게 한다.
set -euo pipefail

FILE="${FILE:-deploy/charts/codekr/values-release.yaml}"
REQUESTED="${REQUESTED:-}"
OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"

tag_in() { sed -n 's|^  imageTag: *||p' "$1" | head -1; }

current=$(tag_in "$FILE")
[ -n "$current" ] || { echo "::error::${FILE} 에서 imageTag 를 읽지 못했습니다." >&2; exit 1; }

if [ -n "$REQUESTED" ]; then
  # **오타 하나가 ImagePullBackOff 다.** 저장소에 없는 커밋이면 여기서 멈춘다 —
  # 이미지가 있는지 보는 것은 그다음 단계이고, 이것은 그보다 싼 검사다.
  target=$(git rev-parse --verify --quiet "${REQUESTED}^{commit}") || {
    echo "::error::'${REQUESTED}' 를 이 저장소에서 찾지 못했습니다. 커밋 sha 를 확인하세요." >&2
    exit 1
  }
else
  # 직전 배포 = 이 파일의 이력에서 **지금과 다른** 가장 최근 값.
  #
  # "한 커밋 전" 이 아니라 "다른 값" 인 이유: 주석만 고친 커밋이 사이에 끼면
  # 한 커밋 전은 같은 값이고, 그러면 되돌리기가 조용히 아무 일도 안 한다.
  target=""
  for commit in $(git log --format=%H -- "$FILE"); do
    value=$(git show "${commit}:${FILE}" 2>/dev/null | sed -n 's|^  imageTag: *||p' | head -1)
    if [ -n "$value" ] && [ "$value" != "$current" ]; then
      target="$value"
      break
    fi
  done
  [ -n "$target" ] || {
    echo "::error::직전 배포를 찾지 못했습니다. 이 파일에 배포 이력이 하나뿐입니다." >&2
    echo "::error::되돌릴 커밋 sha 를 직접 넣어 주세요." >&2
    exit 1
  }
fi

# **같은 sha 면 아무 일도 일어나지 않는다** (#348).
#
# 파일이 안 바뀌면 커밋이 없고, 커밋이 없으면 ArgoCD 는 움직이지 않는다. 여기서
# 성공으로 끝내면 사람은 배포된 줄 알고 돌아간다 — 그것이 이 워크플로가 막아야 할 일이다.
if [ "$target" = "$current" ]; then
  {
    echo "::error::이미 ${current:0:7} 이 배포 태그입니다 — 이 워크플로로는 아무 일도 일어나지 않습니다."
    echo "::error::파일이 안 바뀌면 커밋이 없고, 커밋이 없으면 ArgoCD 는 움직이지 않습니다."
    echo "::error::같은 버전을 다시 맞추려는 것이라면 ArgoCD 에서 Sync(또는 Restart)를 누르세요."
  } >&2
  exit 1
fi

{
  echo "tag=${target}"
  echo "previous=${current}"
} >> "$OUTPUT"

echo "지금: ${current:0:7} → 배포할 것: ${target:0:7}" >&2
