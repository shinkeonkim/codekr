#!/usr/bin/env python3
"""API 가 `/api` 밖에 여는 경로가 인그레스에 있는지 확인한다 (#475).

**배지(`/badge/{handle}.svg`)가 기능이 들어온 날부터 계속 404 였다.** 엔드포인트는
일부러 `/api` 밖에 두었는데(남의 README 에 박히는 주소라 짧아야 한다), 차트의 인그레스는
`/api` 와 `/ws` 만 API 로 보내고 **나머지를 전부 웹으로** 보낸다. 코드도 차트도 각자
맞았고, **둘 사이가 비어 있었다.**

로컬에서는 API 를 직접 부르므로 드러나지 않는다. 통합 시험도 인그레스를 모른다.
그래서 이 검사가 그 자리를 본다.

    python3 scripts/check-ingress-paths.py
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTROLLERS = ROOT / "apps/api/src/main/kotlin"
INGRESS = ROOT / "deploy/charts/codekr/templates/ingress.yaml"

MAPPING = re.compile(r'@(?:Get|Post|Put|Patch|Delete|Request)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"')
CLASS_MAPPING = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"')


def api_prefixes() -> set[str]:
    """컨트롤러가 여는 경로 중 `/api` 밖에 있는 것의 첫 구획."""
    found: set[str] = set()
    for path in CONTROLLERS.rglob("*Controller*.kt"):
        text = path.read_text(encoding="utf-8")
        # **클래스 선언 앞에 있는 것이 클래스 레벨 매핑이다.** 어노테이션 사이에
        # `@Validated` 같은 것이 끼어 있어도 상관없게, 선언 앞부분만 잘라서 본다.
        head = text.split("\nclass ", 1)[0]
        base = ""
        if (klass := CLASS_MAPPING.search(head)) is not None:
            base = klass.group(1)
        for mapping in MAPPING.finditer(text):
            route = mapping.group(1)
            if route == base:
                continue
            full = route if route.startswith("/") and not base else base + route
            if not full.startswith("/") or full.startswith("/api"):
                continue
            segment = "/" + full.strip("/").split("/")[0]
            if segment.startswith("/{"):
                continue
            found.add(segment)
    return found


def ingress_prefixes() -> set[str]:
    """인그레스가 API 로 보내는 경로."""
    text = INGRESS.read_text(encoding="utf-8")
    found: set[str] = set()
    for block in re.finditer(r"- path: (\S+)(.*?)(?=- path: |\Z)", text, re.S):
        if "codekr-api" in block.group(2):
            found.add(block.group(1))
    return found


def leaked_actuator(served: set[str]) -> list[str]:
    """인그레스가 `/actuator` 로 가는 길을 열었는지 본다 (#676).

    `/actuator/prometheus` 는 클러스터 안에서 Prometheus 가 토큰 없이 읽어야 해서
    `SecurityConfig` 에서 `permitAll` 이다. **그 선택이 안전한 이유는 인그레스가
    `/actuator` 를 api 로 보내지 않기 때문뿐이다.** 그러니 그 전제를 여기서 지킨다 —
    누가 인그레스에 넣으면 조용히 열리는 것이 아니라 CI 가 멈춘다.

    **양쪽으로 본다.** `/` 처럼 `/actuator` 를 품는 것도, `/actuator/prometheus` 처럼
    그 아래를 콕 집는 것도 같은 결과를 낸다.
    """
    def touches(prefix: str) -> bool:
        head = prefix.rstrip("/") or "/"
        return "/actuator".startswith(head) or head.startswith("/actuator")

    return sorted(prefix for prefix in served if touches(prefix))


def main() -> int:
    served = ingress_prefixes()
    missing = sorted(prefix for prefix in api_prefixes() if prefix not in served)

    if (leaked := leaked_actuator(served)):
        print("✗ 인그레스가 `/actuator` 를 api 로 보냅니다:")
        for prefix in leaked:
            print(f"    {prefix}  →  deploy/charts/codekr/templates/ingress.yaml")
        print(
            "\n  `/actuator/prometheus` 는 토큰 없이 열려 있습니다 (#676). 클러스터 안에서만"
            "\n  닿아야 하므로, 밖으로 가는 길이 생기면 그 선택이 무너집니다."
            "\n  정말 열어야 한다면 SecurityConfig 의 permitAll 부터 다시 정하세요.",
        )
        return 1

    if missing:
        print("✗ API 가 여는데 인그레스가 웹으로 보내는 경로가 있습니다:")
        for prefix in missing:
            print(f"    {prefix}  →  deploy/charts/codekr/templates/ingress.yaml 에 추가해야 합니다")
        print("\n  그대로 두면 운영에서 404 입니다. 로컬에서는 API 를 직접 불러서 드러나지 않습니다.")
        return 1

    print(f"✓ `/api` 밖 경로가 모두 인그레스에 있습니다 (API 경로: {', '.join(sorted(served))})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
