#!/usr/bin/env bash
# 공식 이미지가 없어 직접 만들어야 하는 런타임 이미지를 빌드한다.
# 목록은 infra/runtimes/images/ 아래 디렉터리 이름에서 읽는다.
#
# 빌더는 CODEKR_IMAGE_BUILDER 로 고른다 (docker | nerdctl).
#
#   **containerd 노드에서는 nerdctl 이어야 한다.** docker 로 빌드하면 이미지가 엔진의
#   저장소에 들어가는데, 실행기는 containerd 의 codekr 네임스페이스를 본다 — 빌드는
#   성공했는데 실행기는 이미지를 못 찾는 상태가 된다.
#
#   nerdctl 은 containerd 소켓을 쓰므로 보통 sudo 가 필요하다:
#     CODEKR_IMAGE_BUILDER=nerdctl sudo -E bash scripts/build-runtimes.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGES_DIR="${ROOT_DIR}/infra/runtimes/images"
REGISTRY="${ROOT_DIR}/infra/runtimes/runtimes.yaml"
BUILDER="${CODEKR_IMAGE_BUILDER:-docker}"
# 실행기가 컨테이너를 만드는 네임스페이스와 같아야 한다 (containerd.go).
NAMESPACE="${CODEKR_CONTAINERD_NAMESPACE:-codekr}"

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
  echo "==> build ${image} (${BUILDER})"
  case "${BUILDER}" in
    docker) docker build -t "${image}" "${directory}" ;;
    nerdctl) nerdctl --namespace "${NAMESPACE}" build -t "${image}" "${directory}" ;;
    *) echo "알 수 없는 빌더: ${BUILDER} (docker | nerdctl)" >&2; exit 1 ;;
  esac
done

echo "완료"
