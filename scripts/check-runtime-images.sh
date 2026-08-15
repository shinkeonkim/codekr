#!/usr/bin/env bash
# 자체 빌드 런타임 이미지가 **레지스트리에 실제로 있는지** 확인한다 (#588).
#
# ## 왜 필요한가
#
# 공개 런타임은 레지스트리가 업스트림에서 당겨 오지만(zot sync), `codekr-runtime-*` 는
# 그 규칙 밖이라 **사람이 밀어야** 한다. 그런데 밀지 않아도 아무 데서도 티가 안 난다.
#
#   - 로컬은 `make build-runtimes` 로 만든 것이 노드에 있어 잘 돈다
#   - CI 는 런타임 매트릭스를 뺀다
#   - **운영에서 그 언어로 처음 채점할 때** 404 로 드러난다
#
# 실제로 아희·엄랭이 그렇게 몇 달을 비어 있었다 (#394 → #588).
#
# ## 사용
#
#   CODEKR_RUNTIME_REGISTRY=registry.example.com scripts/check-runtime-images.sh
#
# 자격증명은 `docker login` 한 것을 그대로 쓴다(`~/.docker/config.json`).
# 없으면 인증이 필요 없는 레지스트리로 보고 그냥 묻는다.
set -euo pipefail

RUNTIMES_FILE="${RUNTIMES_FILE:-infra/runtimes/runtimes.yaml}"
REGISTRY="${CODEKR_RUNTIME_REGISTRY:-}"
SCHEME="${CODEKR_RUNTIME_REGISTRY_SCHEME:-https}"

if [[ -z "$REGISTRY" ]]; then
  echo "CODEKR_RUNTIME_REGISTRY 가 필요합니다 (예: registry.example.com)" >&2
  exit 2
fi

# 자체 빌드 이미지만 본다. 나머지는 레지스트리가 알아서 당겨 온다.
#
# `mapfile` 을 쓰지 않는다 — macOS 가 딸려 보내는 bash 3.2 에 없다.
images=$(python3 - "$RUNTIMES_FILE" <<'PY'
import sys, yaml
for definition in yaml.safe_load(open(sys.argv[1]))["runtimes"]:
    image = definition["image"]
    if image.startswith("codekr-runtime-"):
        print(image)
PY
)

if [[ -z "$images" ]]; then
  echo "자체 빌드 런타임이 없습니다."
  exit 0
fi
total=$(printf '%s\n' "$images" | wc -l | tr -d ' ')

# `docker login` 이 남긴 자격증명을 읽는다. 없으면 빈 값으로 둔다.
#
# **`config.json` 만 보면 안 된다.** Docker Desktop 은 `credsStore` 를 두고 비밀번호를
# 키체인에 넣으므로, 로그인했는데도 `auths` 항목이 비어 있다 — 그 경우 401 을 받고
# "레지스트리에 없다" 고 잘못 말하게 된다. 도우미(`docker-credential-<store>`)에게 묻는다.
auth="$(python3 - "$REGISTRY" <<'PY'
import base64, json, os, subprocess, sys

registry = sys.argv[1]
path = os.path.expanduser(os.environ.get("DOCKER_CONFIG", "~/.docker") + "/config.json")
try:
    config = json.load(open(path))
except OSError:
    config = {}

entry = config.get("auths", {}).get(registry, {})
if entry.get("auth"):
    print(entry["auth"])
    raise SystemExit

store = config.get("credHelpers", {}).get(registry) or config.get("credsStore")
if not store:
    raise SystemExit

try:
    got = subprocess.run(
        ["docker-credential-" + store, "get"],
        input=registry, capture_output=True, text=True, timeout=15,
    )
    creds = json.loads(got.stdout)
    print(base64.b64encode(f"{creds['Username']}:{creds['Secret']}".encode()).decode())
except Exception:
    # 도우미가 없거나 답하지 않으면 인증 없이 물어본다. 401 은 아래에서 구분해 말한다.
    pass
PY
)"

missing=0
for image in $images; do
  name="${image%%:*}"
  tag="${image##*:}"
  url="$SCHEME://$REGISTRY/v2/$name/manifests/$tag"

  # 매니페스트 종류가 여럿이다. 전부 받아들인다고 말하지 않으면 406 이 온다.
  accept='application/vnd.oci.image.index.v1+json,application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.list.v2+json,application/vnd.docker.distribution.manifest.v2+json'

  if [[ -n "$auth" ]]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Basic $auth" -H "Accept: $accept" "$url")
  else
    code=$(curl -s -o /dev/null -w '%{http_code}' -H "Accept: $accept" "$url")
  fi

  case "$code" in
    200) printf '  \033[32m✓\033[0m %-34s %s\n' "$image" "$code" ;;
    401|403) printf '  \033[33m?\033[0m %-34s %s — 자격증명이 없습니다. docker login 후 다시\n' "$image" "$code"; missing=1 ;;
    *) printf '  \033[31m✗\033[0m %-34s %s — 레지스트리에 없습니다\n' "$image" "$code"; missing=1 ;;
  esac
done

if [[ "$missing" == 1 ]]; then
  cat >&2 <<'MSG'

빠진 이미지가 있습니다. 그 런타임의 문제는 운영에서 채점되지 않습니다
(실행기가 404 를 받고 SYSTEM_ERROR 로 끝납니다).

만들어 넣는 방법은 infra/runtimes/images/README.md 의 "새 이미지를 추가할 때" 를 보십시오.
MSG
  exit 1
fi

echo "자체 빌드 런타임 ${total}개가 모두 레지스트리에 있습니다."
