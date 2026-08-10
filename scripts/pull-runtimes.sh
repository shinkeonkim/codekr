#!/usr/bin/env bash
# 코드 실행기가 사용하는 런타임 이미지를 미리 내려받는다.
# 이미지가 없으면 첫 채점이 이미지 pull 대기로 타임아웃 날 수 있다.
#
# 목록은 런타임 정의 파일 하나에서만 읽는다 — 스크립트와 정의가 갈라지지 않게 하기 위함이다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGISTRY="${ROOT_DIR}/infra/runtimes/runtimes.yaml"

list_images() {
  python3 -c '
import sys, yaml
definitions = yaml.safe_load(open(sys.argv[1]))["runtimes"]
for image in dict.fromkeys(d["image"] for d in definitions):
    print(image)
' "${REGISTRY}"
}

# macOS 기본 bash 3.2 에는 mapfile 이 없어 이식성 있게 읽는다.
IMAGES=()
while IFS= read -r image; do
  [[ -n "${image}" ]] && IMAGES+=("${image}")
done < <(list_images)

failed=()
for image in "${IMAGES[@]}"; do
  echo "==> pull ${image}"
  # 자체 빌드가 필요한 이미지는 공개 레지스트리에 없다 — 실패해도 나머지는 계속 받는다.
  docker pull "${image}" > /dev/null || failed+=("${image}")
done

echo "완료: ${#IMAGES[@]}개 중 $(( ${#IMAGES[@]} - ${#failed[@]} ))개 준비됨"
if [[ ${#failed[@]} -gt 0 ]]; then
  echo "받지 못한 이미지: ${failed[*]}" >&2
  echo "자체 빌드가 필요한 이미지는 'make build-runtimes' 로 만든다." >&2
fi
