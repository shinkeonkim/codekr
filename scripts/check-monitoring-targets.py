#!/usr/bin/env python3
"""지표를 내는 워커가 PodMonitor 목록에서 빠지지 않게 한다 (#677).

`PodMonitor` 는 컴포넌트 이름을 **손으로 적은 목록**으로 고른다
(`values: [judge, judge-contest, executor]`). 목록이라 새 워커가 생기면 조용히 빠진다.

**빠진 줄을 알아챌 방법이 없다는 것이 문제다.** 채점기 하나가 안 긁혀도 나머지가
`up=1` 이라 대시보드는 정상으로 보인다. 실제로 #639 가 `judge-contest` 를 새로 만들었고,
그때 이 목록이 있었다면 갈라졌을 것이다.

그래서 **워커 배포가 여는 포트**와 목록을 견준다. `/metrics` 를 내는지까지는 보지 않는다 —
`internal/httpapi/server.go` 가 judge·executor 양쪽에서 같은 mux 를 쓰므로, 워커 이미지를
쓰는 배포는 전부 낸다.

    python3 scripts/check-monitoring-targets.py
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEMPLATES = ROOT / "deploy/charts/codekr/templates"
# 워커, 즉 **호출자가 없어 Service 가 없는** 것들. api·web 은 ServiceMonitor 쪽이다.
WORKER_TEMPLATES = ("judge.yaml", "executor.yaml")

COMPONENT = re.compile(r"app\.kubernetes\.io/component:\s*([\w-]+)")
PODMONITOR_VALUES = re.compile(r"operator:\s*In,\s*values:\s*\[([^\]]*)\]")


def worker_components() -> set[str]:
    """워커 배포가 파드에 붙이는 컴포넌트 이름."""
    found: set[str] = set()
    for name in WORKER_TEMPLATES:
        found |= set(COMPONENT.findall((TEMPLATES / name).read_text(encoding="utf-8")))
    return found


def monitored_components() -> set[str]:
    text = (TEMPLATES / "monitoring.yaml").read_text(encoding="utf-8")
    if (match := PODMONITOR_VALUES.search(text)) is None:
        return set()
    return {value.strip() for value in match.group(1).split(",") if value.strip()}


def main() -> int:
    declared, monitored = worker_components(), monitored_components()

    if missing := sorted(declared - monitored):
        print("✗ 지표가 안 긁히는 워커가 있습니다:")
        for component in missing:
            print(f"    {component}")
        print(
            "\n  deploy/charts/codekr/templates/monitoring.yaml 의 PodMonitor values 에 넣으세요."
            "\n  빠져 있어도 나머지가 up=1 이라 대시보드는 정상으로 보입니다.",
        )
        return 1

    if stale := sorted(monitored - declared):
        # 없는 것을 고르면 Prometheus 는 조용히 아무것도 안 긁는다.
        print(f"✗ PodMonitor 가 없는 컴포넌트를 고릅니다: {', '.join(stale)}")
        return 1

    print(f"✓ 워커 지표 대상이 맞습니다 ({', '.join(sorted(declared))})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
