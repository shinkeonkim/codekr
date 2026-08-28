#!/usr/bin/env python3
"""시크릿·설정을 읽는 워크로드가 재적용 어노테이션을 빠뜨리지 않게 한다 (#713).

`envFrom` 은 **파드가 뜰 때 확정된다.** 시크릿을 고쳐도 도는 파드는 옛 값을 계속 들고
있고, 매니페스트가 안 바뀌었으니 **ArgoCD 는 `Synced` 라고 말한다.** #705 에서 초안
만들기 키가 그렇게 조용히 꺼져 있었다 — 화면 어디에도 신호가 없다.

그래서 Reloader 에게 맡겼는데, **어노테이션은 워크로드마다 손으로 붙는다.** 새 배포가
시크릿을 읽기 시작하면서 이 줄을 잊으면 같은 일이 다시 난다. 그때도 아무 신호가 없다.

**렌더한 결과를 본다** — 템플릿 문자열이 아니라. `envFrom` 이 조건 블록 안에 있을 수
있고(`mail.host` 가 그렇다), 조건이 꺼진 채로는 참조가 아니다.

    python3 scripts/check-reload-annotations.py
"""

import pathlib
import subprocess
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
CHART = ROOT / "deploy/charts/codekr"
ANNOTATION = "reloader.stakater.com/auto"

# 굴릴 파드가 계속 살아 있는 것만 본다. **Job·CronJob 은 뺀다** — 매번 새로 뜨므로
# 다음 실행이 이미 새 값을 든다.
RELOADABLE = {"Deployment", "StatefulSet", "DaemonSet"}


def rendered(values: dict) -> list[dict]:
    args = ["helm", "template", "codekr", str(CHART)]
    for key, value in values.items():
        args += ["--set", f"{key}={value}"]
    done = subprocess.run(args, capture_output=True, text=True)
    if done.returncode != 0:
        print(f"✗ helm template 이 실패했습니다:\n{done.stderr}")
        sys.exit(2)
    return [doc for doc in yaml.safe_load_all(done.stdout) if doc]


def references(spec: dict) -> set[str]:
    """이 파드 명세가 읽는 시크릿·컨피그맵의 이름."""
    found: set[str] = set()
    for container in spec.get("containers", []) + spec.get("initContainers", []):
        for source in container.get("envFrom", []):
            for ref in ("secretRef", "configMapRef"):
                if name := source.get(ref, {}).get("name"):
                    found.add(name)
        for env in container.get("env", []):
            for ref in ("secretKeyRef", "configMapKeyRef"):
                if name := (env.get("valueFrom") or {}).get(ref, {}).get("name"):
                    found.add(name)
    for volume in spec.get("volumes", []):
        for ref in ("secret", "configMap"):
            block = volume.get(ref) or {}
            if name := block.get("secretName") or block.get("name"):
                found.add(name)
    return found


def gaps(docs: list[dict]) -> list[str]:
    missing = []
    for doc in docs:
        if doc.get("kind") not in RELOADABLE:
            continue
        used = references(doc.get("spec", {}).get("template", {}).get("spec", {}))
        if not used:
            continue
        annotations = doc.get("metadata", {}).get("annotations") or {}
        if ANNOTATION not in annotations:
            name = doc["metadata"]["name"]
            missing.append(f"{name} — {', '.join(sorted(used))} 을 읽는데 어노테이션이 없습니다")
    return missing


def main() -> int:
    # **모든 기능을 켠 상태로 본다.** 꺼져 있으면 참조가 아예 안 나온다.
    docs = rendered({
        "litellm.enabled": "true",
        "mail.host": "smtp.example.com",
        "storage.endpoint": "http://minio:9000",
    })

    if problems := gaps(docs):
        print("✗ 시크릿이 바뀌어도 모르는 워크로드가 있습니다:")
        for problem in problems:
            print(f"    {problem}")
        print(
            f'\n  Deployment 의 metadata 에 `{{{{- include "codekr.reloadOnChange" . | nindent 2 }}}}` 를 다세요.'
            "\n  빠져 있어도 배포는 성공하고 ArgoCD 는 Synced 라고 말합니다 (#705).",
        )
        return 1

    watched = sum(1 for doc in docs if (doc.get("metadata", {}).get("annotations") or {}).get(ANNOTATION))
    print(f"✓ 시크릿을 읽는 워크로드가 모두 재적용 대상입니다 ({watched}개)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
