# 테스트 작성 문제 실행 하네스 (#652).
#
# **채점이 뒤집혀 있다.** 다른 유형은 우리가 시험을 숨기고 사용자가 구현을 내지만,
# 여기서는 **사용자가 시험을 내고 우리가 구현을 숨긴다.**
#
#   올바른 구현   → 통과해야 한다 (안 그러면 시험이 틀린 것이다)
#   버그 심은 구현 → 실패해야 한다 (그것을 잡는 것이 시험의 일이다)
#
# 작업 디렉터리에 있을 수 있는 파일:
#   test_solution.py  제출. `unittest.TestCase` 를 담는다
#   reference.py      올바른 구현. 문제가 소유한다
#   mutant_1.py …     버그 심은 구현. 문제가 소유한다
#
# ## 한 번만 실행한다
#
# 구현마다 컨테이너를 띄우면 실행기 부하가 구현 수만큼 는다 — 이 유형이 다른 것보다
# 비싼 이유가 그것이었다. 여기서 반복을 돌면 **실행은 한 번**이고, 시간 제한도 하나다.
# 사용자의 시험이 그 안에 N번 돌 만큼 빨라야 한다는 뜻이고, 그것은 옳은 요구다.
#
# ## 구현이 새면 답이 보인다
#
# `unittest` 의 출력에는 **기댓값과 실제값이 그대로 들어간다**(`AssertionError: 3 != 2`).
# 그것이 사용자에게 가면 버그 심은 구현이 무엇을 하는지 드러난다 — #525 가 SQL 에서
# 짚은 자리와 같다. 그래서 **출력을 버리고 통과 여부만 찍는다.**
#
# ## 표준 라이브러리만 쓴다
#
# `pytest` 를 쓰려면 이미지에 넣어야 하고, 그러면 #588 이 겪은 "레지스트리에 없는
# 이미지" 가 하나 는다. `python -m unittest` 는 파이썬에 들어 있다.
set -eu

cd /work

if [ ! -f reference.py ]; then
    echo "문제에 올바른 구현이 없습니다." >&2
    exit 1
fi

# 구현 하나를 `solution.py` 자리에 놓고 제출한 시험을 돌린다.
#
# **`__pycache__` 를 지운다.** 파이썬은 소스가 바뀌었는지를 **수정 시각(초 단위)과
# 크기**로 판단한다. 구현들을 같은 초에 갈아 끼우는데 길이까지 같으면 — 뮤테이션은
# 대개 글자 하나를 바꾸므로 **길이가 같은 것이 흔하다** — 옛 바이트코드를 그대로 읽는다.
#
# 실측: 지우지 않고 같은 입력을 세 번 돌리니 `PASS FAIL`·`FAIL FAIL`·`PASS PASS` 로
# **매번 달랐다.** 지우면 다섯 번 모두 같다. 판정이 흔들리는 것이 가장 나쁘다 —
# 사용자는 자기 시험이 틀렸다고 믿고, 다시 내면 통과한다.
run_against() {
    cp "$1" solution.py
    rm -rf __pycache__
    if python3 -m unittest -q test_solution >/dev/null 2>&1; then
        echo "PASS"
    else
        echo "FAIL"
    fi
}

# **기대값은 문제의 구조가 정한다** — 정규식(#653)과 같다. 올바른 구현은 통과,
# 버그 심은 것은 전부 실패다. 정답 시험을 따로 두어 기대값을 만들지 않는 이유:
# 그러면 **출제자가 놓친 버그는 아무도 잡지 못한다.**
mutants=$(ls mutant_*.py 2>/dev/null | sort -V || true)

echo "--- codekr:expected"
echo "PASS"
for _ in $mutants; do echo "FAIL"; done

echo "--- codekr:actual"
run_against reference.py
for mutant in $mutants; do
    run_against "$mutant"
done
