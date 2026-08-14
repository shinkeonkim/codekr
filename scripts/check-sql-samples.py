#!/usr/bin/env python3
"""SQL 문제 지문의 예시 데이터가 **실제 시드와 같은지** 확인한다 (#526).

지문에 값을 적는 것의 위험은 하나다 — **시드가 바뀌면 지문이 거짓이 된다.** 그것은
값이 없는 것보다 나쁘다. 없으면 실행해 보면 되지만, 틀린 값은 사람을 잘못된 곳으로
데려간다.

그래서 이 스크립트가 지문의 표를 읽어 시드 SQL 의 `INSERT` 와 맞춰 본다. `make`
없이도 돌아가야 해서 DB 를 띄우지 않고 파일만 읽는다.

    python3 scripts/check-sql-samples.py
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SEEDS = ROOT / "scripts" / "seed-problems"


def seed_rows(schema_sql: str) -> dict[str, set[tuple[str, ...]]]:
    """시드 SQL 의 INSERT 를 표 이름 → 행 집합으로 읽는다."""
    rows: dict[str, set[tuple[str, ...]]] = {}
    for match in re.finditer(
        r"INSERT INTO (\w+)\s*\([^)]*\)\s*VALUES(.*?);", schema_sql, re.S
    ):
        table, body = match.group(1), match.group(2)
        for line in re.finditer(r"\(([^()]*)\)", body):
            values = [value.strip() for value in split_values(line.group(1))]
            rows.setdefault(table, set()).add(tuple(normalize(value) for value in values))
    return rows


def split_values(text: str) -> list[str]:
    """따옴표 안의 쉼표에서 자르지 않는다."""
    parts, cell, quoted = [], "", False
    for char in text:
        if char == "'":
            quoted = not quoted
            cell += char
        elif char == "," and not quoted:
            parts.append(cell)
            cell = ""
        else:
            cell += char
    parts.append(cell)
    return parts


def normalize(value: str) -> str:
    """`DATE '2024-03-02'` · `'서울'` · `28000` · `NULL` 을 같은 모양으로 만든다."""
    value = value.strip()
    value = re.sub(r"^DATE\s+", "", value)
    if value.upper() == "NULL":
        return "(NULL)"
    return value.strip("'")


def statement_rows(description: str) -> list[tuple[str, ...]]:
    """지문의 마크다운 표에서 값 행만 뽑는다. 머리글과 `…` 줄은 뺀다."""
    found = []
    for line in description.splitlines():
        line = line.strip()
        if not line.startswith("|") or set(line) <= set("|- "):
            continue
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if not cells or cells[0] in {"…", ""} or not cells[0].isdigit():
            continue
        found.append(tuple(cells))
    return found


def main() -> int:
    problems = [
        path for path in sorted(SEEDS.glob("*.json"))
        if json.loads(path.read_text(encoding="utf-8")).get("problemKind") == "JUDGE_SQL"
    ]
    failures = 0
    for path in problems:
        data = json.loads(path.read_text(encoding="utf-8"))
        schema = (SEEDS / data["sqlSchemaFile"]).read_text(encoding="utf-8")
        known = seed_rows(schema)
        every_row = {row for rows in known.values() for row in rows}

        shown = statement_rows(data["description"])
        if not shown:
            print(f"✗ {path.name}: 지문에 예시 데이터가 없습니다")
            failures += 1
            continue
        for row in shown:
            if row not in every_row:
                print(f"✗ {path.name}: 시드에 없는 행이 지문에 있습니다 — {row}")
                failures += 1
        print(f"✓ {path.name}: 예시 {len(shown)}행이 모두 시드와 일치")

    if failures:
        print(f"\n{failures}건이 어긋납니다. 지문이 시드보다 오래된 것입니다.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
