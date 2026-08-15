#!/usr/bin/env python3
"""시드 문제를 **문제별 묶음(zip)** 으로 만든다 (#594).

`scripts/seed-problems` 는 평면이다 — `08-sql-seoul-members.json` 과, 다섯 문제가
함께 쓰는 `sql/library.sql`. **원본을 그렇게 두는 이유는 #313 이 정했다**: 스키마를
문제마다 복제하면 한 글자 고칠 때 다섯 파일을 고친다.

그런데 묶음 하나에는 **그 문제가 가리키는 스키마만** 들어가야 한다 (#479 의 "아무도 안
가리키는 파일은 거절한다"). 그래서 사람이 손으로 폴더를 만들면 매번 "이 문제는 어느
스키마를 쓰나" 를 판단해야 하고, 실제로 안 쓰는 파일이 섞여 거절당했다.

**그 판단을 사람에게 시키지 않는다.** `problem.json` 이 이미 답을 갖고 있다.

    python3 scripts/pack-problems.py             # 전부
    python3 scripts/pack-problems.py 08 16       # 이름에 그 글자가 든 것만

만들어진 zip 은 `build/problem-bundles/` 에 놓인다.
"""

import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SEEDS = ROOT / "scripts" / "seed-problems"
OUT = ROOT / "build" / "problem-bundles"


def bundle(seed: pathlib.Path) -> tuple[pathlib.Path, list[str]]:
    """문제 하나를 zip 으로. 넣은 파일 목록을 함께 돌려준다."""
    data = json.loads(seed.read_text(encoding="utf-8"))
    packed = ["problem.json"]

    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"{seed.stem}.zip"

    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
        # **problem.json 은 그대로 넣는다.** 시드 파일과 묶음이 같은 형식이라는 것이
        # 이 형식의 전제다 (#479) — 여기서 손대기 시작하면 둘이 갈라진다.
        zf.writestr("problem.json", json.dumps(data, ensure_ascii=False, indent=2) + "\n")

        # 그 문제가 **가리키는** 스키마만. 이것이 이 스크립트가 있는 이유다.
        schema = data.get("sqlSchemaFile")
        if schema:
            source = SEEDS / schema
            if not source.exists():
                raise SystemExit(f"✗ {seed.name}: sqlSchemaFile 이 가리키는 {schema} 가 없습니다")
            zf.writestr(schema, source.read_text(encoding="utf-8"))
            packed.append(schema)

        # 테스트케이스를 파일로 가진 문제는 아직 없다. 생기면 여기서 함께 싼다 —
        # `testcases/{seq}.in|.out` 이 형식이다 (#479).
        cases = SEEDS / seed.stem / "testcases"
        if cases.is_dir():
            for path in sorted(cases.iterdir()):
                if path.suffix in {".in", ".out"}:
                    zf.writestr(f"testcases/{path.name}", path.read_text(encoding="utf-8"))
                    packed.append(f"testcases/{path.name}")

    return target, packed


def main() -> int:
    filters = sys.argv[1:]
    seeds = [
        path for path in sorted(SEEDS.glob("*.json"))
        if not filters or any(word in path.name for word in filters)
    ]
    if not seeds:
        print("만들 문제가 없습니다.")
        return 1

    for seed in seeds:
        target, packed = bundle(seed)
        print(f"✓ {target.relative_to(ROOT)}  ({', '.join(packed)})")

    print(f"\n{len(seeds)}개를 만들었습니다. 어드민 → 문제 → 묶음 올리기 에서 올리면 됩니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
