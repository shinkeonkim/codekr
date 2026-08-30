#!/usr/bin/env python3
"""라이브 샌드박스 시험이 CI 에서 실제로 도는지 본다 (#728).

**목록에 없는 다섯이 조용히 안 돌고 있었다.** 필터가 돌릴 시험의 이름을 손으로 적는
방식이었고, 새로 붙인 시험은 거기 없으면 그만이었다 — 그중 셋은 실제 사고
(#709·#715·#716)를 고치며 붙인 것이라, **그 사고가 다시 나도 CI 는 초록**이었다.

필터를 "이것만 빼고 다 돈다" 로 뒤집었으므로 새 시험은 저절로 들어온다. 이 검사가 보는
것은 **그 뒤집은 것이 다시 좁아지지 않는가**이다.

    python3 scripts/check-live-tests.py
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CI = ROOT / ".github/workflows/ci.yml"
SANDBOX = ROOT / "apps/executor/internal/sandbox"

RUN = re.compile(r"-test\.run\s+'([^']*)'")
SKIP = re.compile(r"-test\.skip\s+'([^']*)'")
TEST_NAME = re.compile(r"^func (TestLive\w*)\(", re.M)


def live_tests() -> set[str]:
    found: set[str] = set()
    for path in SANDBOX.glob("*_test.go"):
        found |= set(TEST_NAME.findall(path.read_text(encoding="utf-8")))
    return found


def main() -> int:
    text = CI.read_text(encoding="utf-8")
    problems: list[str] = []

    # 샌드박스 잡의 필터 하나만 본다. selftest 잡도 `-test.run TestLive` 를 쓰지만
    # 그쪽은 이 폴더의 시험이 아니다.
    runs = [value for value in RUN.findall(text) if value.startswith("TestLive")]
    if "TestLive" not in runs:
        problems.append(
            "샌드박스 잡의 `-test.run` 이 `TestLive` 가 아닙니다. "
            f"지금: {runs}\n    이름을 골라 적으면 새 시험이 조용히 빠집니다 (#728).",
        )

    known = live_tests()
    for pattern in SKIP.findall(text):
        for name in pattern.split("|"):
            name = name.strip()
            # **없는 것을 빼고 있으면 알린다.** 시험 이름이 바뀌면 그 예외는 아무것도
            # 안 막으면서 "예외가 있다" 는 인상만 남긴다.
            if name and name not in known:
                problems.append(f"`-test.skip` 이 없는 시험을 가리킵니다: {name}")

    if problems:
        print("✗ 라이브 시험 필터에 문제가 있습니다:")
        for problem in problems:
            print(f"    {problem}")
        return 1

    print(f"✓ 라이브 시험 {len(known)}개가 전부 CI 대상입니다")
    return 0


if __name__ == "__main__":
    sys.exit(main())
