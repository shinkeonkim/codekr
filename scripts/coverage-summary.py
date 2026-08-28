#!/usr/bin/env python3
"""세 스택의 커버리지를 한 표로 모은다 (#642).

**도구가 셋이라 형식도 셋이다** — JaCoCo XML, lcov, go coverprofile.
여기서 그것을 읽어 같은 단위(줄)로 바꾼다. 사람이 보는 곳은 하나여야 하기 때문이다.

**없는 것은 없다고 적는다.** 안 돌린 스택을 0% 로 적으면 "시험이 없다" 와
"안 쟀다" 가 구별되지 않고, 그 둘은 완전히 다른 상태다.

    python3 scripts/coverage-summary.py build/coverage
"""

import html
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# (이름, 사람이 열 파일) — 순서가 표의 순서다.
#
# **Go 쪽은 손으로 적지 않는다** (#668). 모듈이 셋이 되면서 `executor`·`judge` 만
# 적힌 이 목록이 세 번째를 조용히 빠뜨렸다. 잰 것이 표에 안 나오면 안 잰 것과
# 구별되지 않는다 — 있는 파일에서 읽는다.
LINKS = {
    "api": "api/index.html",
    "web": "web/summary.txt",
}


def go_modules(out: Path) -> list[str]:
    """실제로 만들어진 커버리지 프로파일에서 모듈 이름을 읽는다."""
    return sorted(path.stem for path in (out / "go").glob("*.out"))


def jacoco(path: Path):
    """JaCoCo XML 의 LINE 카운터. (덮은 줄, 전체 줄)"""
    if not path.exists():
        return None
    root = ET.parse(path).getroot()
    for counter in root.findall("counter"):
        if counter.get("type") == "LINE":
            covered = int(counter.get("covered"))
            return covered, covered + int(counter.get("missed"))
    return None


def lcov(path: Path):
    """lcov 의 `DA:<줄>,<실행횟수>` 를 센다.

    **bun 은 시험이 불러들인 파일만 넣는다.** 한 번도 import 되지 않은 파일은
    0% 가 아니라 아예 빠진다 — 그래서 이 숫자는 실제보다 높다. 표에 그렇게 적는다.
    """
    if not path.exists():
        return None
    total = hit = 0
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.startswith("DA:"):
            total += 1
            if not line.rsplit(",", 1)[-1].strip() == "0":
                hit += 1
    return (hit, total) if total else None


def coverprofile(path: Path):
    """go coverprofile. 줄이 아니라 **문장(statement)** 단위다 — 그것이 go 의 단위다."""
    if not path.exists():
        return None
    total = hit = 0
    pattern = re.compile(r"^.+:\d+\.\d+,\d+\.\d+ (\d+) (\d+)$")
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = pattern.match(line)
        if not match:
            continue
        statements, count = int(match.group(1)), int(match.group(2))
        total += statements
        if count > 0:
            hit += statements
    return (hit, total) if total else None


def collect(out: Path):
    rows = {
        "api": jacoco(out / "api.xml"),
        "web": lcov(out / "web" / "lcov.info"),
    }
    for module in go_modules(out):
        rows[f"go · {module}"] = coverprofile(out / "go" / f"{module}.out")
        LINKS[f"go · {module}"] = f"go/{module}.html"
    return rows


def percent(value):
    hit, total = value
    return 100.0 * hit / total if total else 0.0


def print_table(rows):
    print(f"  {'스택':<14} {'덮인 줄':>16}   비율")
    print(f"  {'-' * 14} {'-' * 16}   ----")
    for name, value in rows.items():
        if value is None:
            print(f"  {name:<14} {'(재지 않음)':>16}")
            continue
        hit, total = value
        print(f"  {name:<14} {hit:>7,} / {total:<6,} {percent(value):>6.1f}%")


def bar(value):
    if value is None:
        return '<td class="none" colspan="2">재지 않음</td>'
    hit, total = value
    ratio = percent(value)
    tone = "low" if ratio < 40 else "mid" if ratio < 70 else "high"
    return (
        f'<td class="num">{hit:,} / {total:,}</td>'
        f'<td class="bar"><span class="{tone}" style="width:{ratio:.1f}%"></span>'
        f"<b>{ratio:.1f}%</b></td>"
    )


def write_index(out: Path, rows):
    body = []
    for name, value in rows.items():
        link = LINKS[name]
        target = out / link
        label = (
            f'<a href="{html.escape(link)}">{html.escape(name)}</a>'
            if target.exists()
            else html.escape(name)
        )
        body.append(f"<tr><th>{label}</th>{bar(value)}</tr>")

    (out / "index.html").write_text(
        INDEX.replace("{{rows}}", "\n".join(body)), encoding="utf-8"
    )


INDEX = """<!doctype html>
<meta charset="utf-8"><title>코드.kr 커버리지</title>
<style>
 body{font:15px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:3rem auto;max-width:44rem;padding:0 1rem}
 table{border-collapse:collapse;width:100%} th,td{padding:.5rem .6rem;border-bottom:1px solid #e5e5e5;text-align:left}
 th{font-weight:600} .num{font-variant-numeric:tabular-nums;color:#666;white-space:nowrap}
 .bar{width:45%;position:relative} .bar span{display:inline-block;height:.7rem;border-radius:.35rem;vertical-align:middle}
 .bar b{margin-left:.5rem;font-variant-numeric:tabular-nums} .low{background:#e5484d} .mid{background:#f5a524} .high{background:#30a46c}
 .none{color:#999} p{color:#555} code{background:#f4f4f4;padding:.1rem .3rem;border-radius:.2rem}
</style>
<h1>코드.kr 커버리지</h1>
<table>{{rows}}</table>
<p><b>api</b> 는 단위 시험과 통합 시험을 합친 것입니다. <b>go</b> 는 줄이 아니라 문장 단위입니다.</p>
<p><b>web 의 숫자는 실제보다 높습니다.</b> bun 은 시험이 불러들인 파일만 세고, 한 번도
import 되지 않은 파일은 0% 가 아니라 아예 빠집니다.</p>
<p>다시 재려면 <code>make coverage</code>.</p>
"""


def write_markdown(out: Path, rows):
    """CI 의 잡 요약에 붙일 표.

    **아티팩트만 올리면 아무도 안 본다** — 받아서 풀고 열어야 하기 때문이다.
    숫자는 열자마자 보여야 한다.
    """
    lines = ["| 스택 | 덮인 줄 | 비율 |", "|---|---:|---:|"]
    for name, value in rows.items():
        if value is None:
            continue
        hit, total = value
        lines.append(f"| {name} | {hit:,} / {total:,} | {percent(value):.1f}% |")
    (out / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "build/coverage")
    rows = collect(out)
    print_table(rows)
    write_index(out, rows)
    write_markdown(out, rows)
    if all(value is None for value in rows.values()):
        print("\n✗ 아무것도 재지 못했습니다.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
