#!/usr/bin/env bash
# 코드 실행기가 사용하는 런타임 이미지를 미리 내려받는다.
# 이미지가 없으면 첫 채점이 이미지 pull 대기로 타임아웃 날 수 있다.
#
# 목록은 런타임 정의 파일 하나에서만 읽는다 — 스크립트와 정의가 갈라지지 않게 하기 위함이다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGISTRY="${ROOT_DIR}/infra/runtimes/runtimes.yaml"

# **다이제스트가 있으면 그것으로 받는다.**
#
# 태그로만 받으면 정의 파일이 고정한 것과 **다른 이미지**가 로컬에 들어온다. 태그는 다시
# 붙기 때문이다. 그러면 실행기가 고정된 다이제스트를 찾지 못해 모든 채점이
# `No such image` 로 실패한다 — 실제로 그랬다 (#96 검수).
list_images() {
  python3 -c '
import sys, yaml
definitions = yaml.safe_load(open(sys.argv[1]))["runtimes"]
seen = {}
for definition in definitions:
    image, digest = definition["image"], definition.get("digest")
    reference = f"{image.split(chr(58))[0]}@{digest}" if digest else image
    seen.setdefault(reference, None)
for reference in seen:
    print(reference)
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
