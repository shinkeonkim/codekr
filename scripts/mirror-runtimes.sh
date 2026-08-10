#!/usr/bin/env bash
# 런타임 이미지를 자체 레지스트리로 미러링하고 다이제스트를 고정한다 (#96).
#
# **태그는 다시 붙을 수 있다.** python:3.13-alpine 이 어제와 오늘 다른 이미지일 수 있고,
# 그러면 우리가 검증한 것과 다른 것이 실행 노드에 들어간다 — 격리가 이미지 내용에
# 의존하는 이 프로젝트에서는 검증 결과의 의미가 약해진다.
#
# 미러링은 매니페스트를 **그대로 복사**하므로 원본과 미러의 다이제스트가 같다.
# 그래서 어느 쪽에서 받아도 같은 것이 온다.
#
# 사용:
#   CODEKR_RUNTIME_REGISTRY=registry.example.com scripts/mirror-runtimes.sh          # 미러링만
#   CODEKR_RUNTIME_REGISTRY=registry.example.com scripts/mirror-runtimes.sh --pin    # 다이제스트도 기록
set -euo pipefail

RUNTIMES_FILE="${RUNTIMES_FILE:-infra/runtimes/runtimes.yaml}"
REGISTRY="${CODEKR_RUNTIME_REGISTRY:-}"
PIN=false
[[ "${1:-}" == "--pin" ]] && PIN=true

if [[ -z "$REGISTRY" ]]; then
  echo "CODEKR_RUNTIME_REGISTRY 가 필요합니다 (예: registry.example.com)" >&2
  exit 1
fi

# crane 은 로컬에 이미지를 받지 않고 레지스트리끼리 복사한다 — 디스크와 시간이 훨씬 덜 든다.
if ! command -v crane >/dev/null 2>&1; then
  echo "crane 이 필요합니다: https://github.com/google/go-containerregistry" >&2
  exit 1
fi

images=$(grep -E '^\s+image:' "$RUNTIMES_FILE" | sed 's/.*image: *"\{0,1\}//; s/"\{0,1\}$//' | sort -u)

for image in $images; do
  target="$REGISTRY/$image"
  echo "==> $image → $target"
  crane copy "$image" "$target"

  if [[ "$PIN" == true ]]; then
    digest=$(crane digest "$target")
    echo "    $digest"
    # 그 이미지를 쓰는 모든 런타임 항목에 다이제스트를 적는다.
    # 이미 있으면 바꾸고, 없으면 image 줄 다음에 넣는다.
    python3 - "$RUNTIMES_FILE" "$image" "$digest" <<'PY'
import re
import sys

path, image, digest = sys.argv[1], sys.argv[2], sys.argv[3]
lines = open(path, encoding="utf-8").read().split("\n")
out = []
index = 0
while index < len(lines):
    line = lines[index]
    out.append(line)
    match = re.match(r'^(\s+)image:\s*"?([^"\s]+)"?\s*$', line)
    if match and match.group(2) == image:
        indent = match.group(1)
        # 바로 다음 줄이 digest 면 갈아 끼운다.
        if index + 1 < len(lines) and re.match(r'^\s+digest:', lines[index + 1]):
            index += 1
        out.append(f'{indent}digest: "{digest}"')
    index += 1
open(path, "w", encoding="utf-8").write("\n".join(out))
PY
  fi
done

echo "완료. 실행 노드에 CODEKR_RUNTIME_REGISTRY=$REGISTRY 를 설정하십시오."
