#!/usr/bin/env bash
#
# Flyway 버전이 겹치는지 본다 (#519).
#
# **이 검사가 필요한 이유는 겹침이 브랜치 안에서는 안 보이기 때문이다.** 스택 둘이
# 각자 V50 을 집으면 각 브랜치는 멀쩡하고 CI 도 초록이다. 겹침은 **둘 다 main 에
# 들어온 뒤에야** 생긴다. 그래서 자기 브랜치만 보면 안 되고 **합쳐진 결과를 본다.**
#
# 실제로 겪었다 — 소속 스택과 런타임 스택이 V49·V50 을 같이 집었고, 두 번째가 머지된
# 순간부터 API 가 아예 뜨지 않았다. 프로덕션도 13시간 동안 CrashLoopBackOff 였다.
#
# 손으로 돌려 볼 수 있다:
#
#   BASE_REF=origin/main .github/scripts/check-migration-versions.sh
#
set -euo pipefail

MIGRATION_DIR="${MIGRATION_DIR:-apps/api/src/main/resources/db/migration}"
BASE_REF="${BASE_REF:-origin/main}"

names_in_tree() {
  git ls-tree --name-only "$1" "$MIGRATION_DIR/" 2>/dev/null \
    | sed 's#.*/##' | grep -E '^V[0-9]+__.*\.sql$' | sort -u || true
}

# 내 것은 **작업 트리**를 본다. 커밋하기 전에도 걸리는 것이 낫다.
mine="$(ls "$MIGRATION_DIR" 2>/dev/null | grep -E '^V[0-9]+__.*\.sql$' | sort -u || true)"

if git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
  # **합쳐진 결과를 3-way 로 계산한다.** main 의 목록에 내 브랜치가 지운 것을 빼고
  # 더한 것을 더한다. 이렇게 해야 **이름 바꾸기가 겹침으로 잘못 잡히지 않는다** —
  # 지우기 + 더하기라서, 합집합만 보면 옛 이름과 새 이름이 둘 다 남는다.
  base="$(names_in_tree "$(git merge-base HEAD "$BASE_REF")")"
  theirs="$(names_in_tree "$BASE_REF")"

  removed_by_me="$(comm -23 <(echo "$base") <(echo "$mine"))"
  merged="$(comm -23 <(echo "$theirs") <(echo "$removed_by_me") | cat - <(echo "$mine") | sort -u)"
  scope="$BASE_REF 와 합쳐진 결과"
else
  echo "!! $BASE_REF 를 찾을 수 없어 이 브랜치 안에서만 봅니다" >&2
  merged="$mine"
  scope="이 브랜치"
fi

merged="$(echo "$merged" | grep -v '^$' || true)"
duplicates="$(echo "$merged" | sed -E 's/^V([0-9]+)__.*/\1/' | sort -V | uniq -d)"

if [ -n "$duplicates" ]; then
  while read -r version; do
    [ -z "$version" ] && continue
    echo "!! 버전 $version 을 여러 파일이 쓰고 있습니다:"
    echo "$merged" | grep -E "^V${version}__" | sed 's/^/     -> /'
  done <<<"$duplicates"

  cat >&2 <<'MSG'

Flyway 는 겹친 버전을 보면 아무것도 실행하지 않고 멈춥니다 — 마이그레이션만 막히는
것이 아니라 **애플리케이션이 아예 뜨지 않습니다.**

이미 배포돼 적용된 번호는 그대로 두고, **아직 한 번도 적용된 적 없는 쪽**의 번호를
비어 있는 다음 번호로 옮기세요. 적용된 것의 번호를 바꾸면 flyway_schema_history 와
어긋나 더 크게 깨집니다.

어느 쪽이 적용됐는지는 DB 에 물어봅니다:

  select version, description from flyway_schema_history order by installed_rank;
MSG
  exit 1
fi

echo "Flyway 버전 겹침 없음 — $(echo "$merged" | grep -c .) 개 ($scope)"
