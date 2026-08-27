#!/usr/bin/env python3
"""대시보드가 없는 지표를 그리고 있지 않은지 본다 (#680).

**그래프가 조용히 빈다는 것이 이 검사의 이유다.** 지표 이름이 바뀌거나 사라져도
Grafana 는 아무 오류도 내지 않는다 — 패널은 그대로 그려지고 선만 없다. 보는 사람은
"제출이 없었나 보다" 로 읽고, 그 상태로 몇 주가 간다.

이름을 만드는 곳은 둘이다.

    libs/gocontract/metrics.go                          judge · executor (#678)
    apps/api/.../observability/MetricNames.kt           api (#684)

대시보드 JSON 의 `codekr_` 로 시작하는 모든 이름이 그 둘 중 하나에서 나와야 한다.

    python3 scripts/check-dashboard-metrics.py
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DASHBOARD = ROOT / "deploy/charts/codekr/files/dashboard.json"
GO_NAMES = ROOT / "libs/gocontract/metrics.go"
KOTLIN_NAMES = ROOT / "apps/api/src/main/kotlin/codekr/api/observability/MetricNames.kt"

# 히스토그램은 `_bucket`·`_sum`·`_count` 로, counter 는 `_total` 로 노출된다.
#
# **양쪽을 같은 방식으로 깎는다.** Go 상수는 `_total` 을 이미 담고 있고
# (`MetricJudgeVerdicts = "codekr_judge_verdicts_total"`), Micrometer 는 담지 않는다
# (`codekr.submissions.stale.closed` 에 노출 시점에 붙는다). 한쪽만 깎으면 둘 다
# 맞는데도 안 맞는 것으로 나온다 — 이 검사를 처음 돌렸을 때 실제로 그랬다.
SUFFIXES = ("_bucket", "_sum", "_count", "_total")


def base(name: str) -> str:
    """노출 접미사를 뗀 이름."""
    for suffix in SUFFIXES:
        if name.endswith(suffix):
            return name[: -len(suffix)]
    return name

USED = re.compile(r"\bcodekr_[a-z_]+")
GO_CONST = re.compile(r'"(codekr_[a-z_]+)"')
# Micrometer 는 점으로 선언하고 밑줄로 내보낸다. counter 에는 `_total` 이 붙는다.
KOTLIN_CONST = re.compile(r'"(codekr(?:\.[a-z]+)+)"')


def used_in_dashboard() -> set[str]:
    """대시보드가 쓰는 이름. 노출 접미사는 떼어 낸다."""
    found: set[str] = set()
    for panel in json.loads(DASHBOARD.read_text(encoding="utf-8"))["panels"]:
        for target in panel.get("targets", []):
            found.update(base(name) for name in USED.findall(target.get("expr", "")))
    return found


def declared() -> set[str]:
    names = {base(name) for name in GO_CONST.findall(GO_NAMES.read_text(encoding="utf-8"))}
    names |= {
        base(dotted.replace(".", "_"))
        for dotted in KOTLIN_CONST.findall(KOTLIN_NAMES.read_text(encoding="utf-8"))
    }
    return names


def main() -> int:
    unknown = sorted(used_in_dashboard() - declared())

    if unknown:
        print("✗ 대시보드가 아무도 안 내보내는 지표를 그립니다:")
        for name in unknown:
            print(f"    {name}")
        print(
            "\n  Grafana 는 이것을 오류로 알리지 않습니다 — 패널은 그려지고 선만 없습니다."
            "\n  이름을 고치거나, libs/gocontract/metrics.go · MetricNames.kt 에 추가하세요.",
        )
        return 1

    print(f"✓ 대시보드가 그리는 codekr 지표가 모두 선언돼 있습니다 ({len(used_in_dashboard())}개)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
