# 정규식 문제 실행 하네스 (#653).
#
# **제출이 코드가 아니라 패턴 하나다.** 그래서 다른 하네스와 하는 일이 다르다 —
# SQL·Redis 는 제출을 *실행*하지만 여기서는 제출을 **자료로 읽어** 엔진에 넘긴다.
#
# 작업 디렉터리에 있을 수 있는 파일:
#   pattern.txt   제출. **패턴 한 줄이다.** 코드가 아니므로 실행하지 않는다
#   cases.txt     문제가 소유한다. 한 줄에 하나, 첫 글자가 판정(`+`/`-`)이다
#   mode.txt      `full` 또는 `search`, 그리고 `i` 가 있으면 대소문자를 무시한다
#
# ## 왜 정답 패턴이 없는가
#
# SQL·Redis 는 정답을 돌려 기대값을 만든다. 정규식은 그럴 필요가 없다 —
# **"이 줄은 맞아야 한다" 가 곧 기대값**이기 때문이다. 정답 패턴으로 기대값을 만들면
# 출제자가 실수한 패턴이 그대로 정답이 되어 **아무도 그것을 잡을 수 없다.**
#
# ## 재앙적 백트래킹은 시간 제한이 잡는다
#
# 사용자가 `(a+)+$` 같은 패턴을 낼 수 있고 파이썬의 `re` 는 백트래킹 엔진이라
# 실제로 매달린다. **그것을 여기서 막지 않는다** — 샌드박스의 시간 제한이 컨테이너째로
# 끊고, 사용자는 `TIME_LIMIT_EXCEEDED` 를 받는다. 그것이 정확한 답이다: 그 패턴은
# 실제로 느리다. 막는 쪽을 택하면 어떤 패턴이 위험한지 우리가 판정해야 하고,
# 그 판정은 엔진마다 다르다.
set -eu

if [ ! -f /work/cases.txt ]; then
    echo "문제에 확인할 문자열이 없습니다." >&2
    exit 1
fi

# **기대값은 문제가 적은 그대로다.** 첫 글자만 읽어 옮긴다.
echo "--- codekr:expected"
while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
        '+'*) echo "MATCH" ;;
        '-'*) echo "NO" ;;
        # 빈 줄은 자료가 아니다 — 파일 끝의 개행 하나로 판정이 하나 늘면 안 된다.
        '') ;;
        *) echo "확인할 문자열은 + 또는 - 로 시작해야 합니다: $line" >&2; exit 1 ;;
    esac
done </work/cases.txt

echo "--- codekr:actual"
# **패턴을 코드로 실행하지 않는다.** 파일에서 읽어 문자열로 넘긴다 — 그러지 않으면
# 제출이 곧 임의 코드 실행이 된다.
exec python3 - <<'PY'
import re
import sys

pattern = open("/work/pattern.txt", encoding="utf-8").read()
# 마지막 개행 하나만 뗀다. 패턴 안의 공백은 자료일 수 있다 (`\s` 대신 실제 공백).
pattern = pattern[:-1] if pattern.endswith("\n") else pattern

mode = ""
try:
    mode = open("/work/mode.txt", encoding="utf-8").read().strip()
except FileNotFoundError:
    pass

flags = re.IGNORECASE if "i" in mode.split(":")[1:] else 0
full = mode.split(":")[0] == "full"

try:
    compiled = re.compile(pattern, flags)
except re.error as error:
    # **문법이 틀린 것은 오답이 아니다.** 무엇이 틀렸는지 보여야 고칠 수 있다.
    print(f"정규식 문법이 올바르지 않습니다: {error}", file=sys.stderr)
    raise SystemExit(1)

with open("/work/cases.txt", encoding="utf-8") as cases:
    for line in cases:
        line = line.rstrip("\n")
        if not line:
            continue
        subject = line[1:]
        hit = compiled.fullmatch(subject) if full else compiled.search(subject)
        print("MATCH" if hit else "NO")
PY
