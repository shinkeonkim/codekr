#!/usr/bin/env bash
# 코드 실행기가 사용하는 런타임 베이스 이미지를 미리 내려받는다.
# 이미지가 없으면 첫 채점이 이미지 pull 대기로 타임아웃 날 수 있다.
set -euo pipefail

IMAGES=(
  "python:3.12-alpine"
  "node:22-alpine"
  "gcc:13"
)

for image in "${IMAGES[@]}"; do
  echo "==> pull ${image}"
  docker pull "${image}"
done

echo "완료: ${#IMAGES[@]}개 런타임 이미지 준비됨"
