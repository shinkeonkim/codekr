#!/usr/bin/env bash
# 공식 이미지가 없어 직접 만들어야 하는 런타임 이미지를 빌드한다.
# 목록은 infra/runtimes/images/ 아래 디렉터리 이름에서 읽는다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGES_DIR="${ROOT_DIR}/infra/runtimes/images"
REGISTRY="${ROOT_DIR}/infra/runtimes/runtimes.yaml"

# 정의 파일에서 해당 언어의 이미지 태그를 찾아 쓴다 — 태그를 두 곳에 적지 않기 위함이다.
tag_for() {
  python3 -c '
import sys, yaml
language = sys.argv[2]
for definition in yaml.safe_load(open(sys.argv[1]))["runtimes"]:
    image = definition["image"]
    if f"codekr-runtime-{language}:" in image:
        print(image)
        break
' "${REGISTRY}" "$1"
}

for directory in "${IMAGES_DIR}"/*/; do
  language="$(basename "${directory}")"
  image="$(tag_for "${language}")"
  if [[ -z "${image}" ]]; then
    echo "건너뜀: ${language} — runtimes.yaml 에 해당 이미지가 없습니다" >&2
    continue
  fi
  echo "==> build ${image}"
  docker build -t "${image}" "${directory}"
done

echo "완료"
