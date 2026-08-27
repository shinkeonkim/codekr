#!/usr/bin/env python3
"""api egress 정책을 켤 때 빠진 것이 없는지 본다 (#664).

**나가는 곳이 일곱인데 넷은 막혀도 조용하다.** 업로드(#424)·메일(#355)·스케일
조정(#390)·초안 만들기는 막혀도 나머지가 멀쩡히 돌아서, 배포한 사람은 성공했다고
믿는다. 그리고 그것을 알아채는 것은 대개 그 기능을 쓰려던 사용자다.

그래서 **설정이 말하는 나가는 곳**과 **정책이 여는 곳**을 견준다. 정책은 차트가 아는
값(DNS·Postgres·Redis·LiteLLM)은 스스로 열지만, 차트가 알 수 없는 둘은 사람이 값을
채워야 한다 — 그 둘이 빈 채로 정책만 켜지는 것이 이 검사가 막는 일이다.

    python3 scripts/check-api-egress.py
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
VALUES = ROOT / "deploy/charts/codekr/values.yaml"
POLICY = ROOT / "deploy/charts/codekr/templates/networkpolicy-api.yaml"
SCALE_CLIENT = ROOT / "apps/api/src/main/kotlin/codekr/api/scaling/service/KubernetesScaleClient.kt"
STORAGE = ROOT / "apps/api/src/main/kotlin/codekr/api/storage/S3ObjectStorage.kt"
MAIL = ROOT / "apps/api/src/main/kotlin/codekr/api/common/mail/MailSender.kt"

# 정책이 반드시 다뤄야 하는 나가는 곳. **코드에 그것을 부르는 곳이 살아 있는 동안만**
# 요구한다 — 기능이 사라지면 규칙도 사라져야 하고, 그것을 사람이 기억하게 두지 않는다.
REQUIRED = [
    ("쿠버네티스 API 서버 (스케일 조정)", "kubernetesApiCidr", SCALE_CLIENT),
    ("MinIO (업로드)", "api.networkPolicy.storage", STORAGE),
    ("SMTP (메일)", "mail.host", MAIL),
]


def policy_text() -> str:
    return POLICY.read_text(encoding="utf-8")


def missing_rules() -> list[str]:
    """정책 템플릿이 다루지 않는 나가는 곳."""
    text = policy_text()
    gaps = []
    for label, marker, source in REQUIRED:
        if not source.exists():
            # 부르는 코드가 사라졌으면 규칙도 필요 없다.
            continue
        key = marker.rsplit(".", 1)[-1]
        if key not in text:
            gaps.append(f"{label} — 정책에 `{marker}` 를 쓰는 규칙이 없습니다")
    return gaps


def enabled_by_default() -> bool:
    """`enabled: true` 로 들어오는 것을 막는다.

    **확인은 클러스터에서만 된다.** 켠 채로 병합되면 다음 배포에서 넷이 조용히 죽고,
    그때 원인을 이 커밋에서 찾기 어렵다.
    """
    text = VALUES.read_text(encoding="utf-8")
    block = text[text.index("  networkPolicy:") :]
    return re.match(r"\s*networkPolicy:\s*\n\s*enabled:\s*true", block) is not None


def main() -> int:
    problems = missing_rules()
    if enabled_by_default():
        problems.append(
            "`api.networkPolicy.enabled` 가 기본 켜짐입니다. "
            "확인은 클러스터에서만 되므로 기본은 꺼짐이어야 합니다 (docs/09 의 절차)",
        )

    if problems:
        print("✗ api egress 정책에 빠진 것이 있습니다:")
        for problem in problems:
            print(f"    {problem}")
        print("\n  막혀도 조용한 것들입니다 — 업로드·메일·스케일 조정은 나머지가 멀쩡히 돕니다.")
        return 1

    print("✓ api egress 정책이 나가는 곳을 모두 다룹니다 (기본은 꺼짐)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
