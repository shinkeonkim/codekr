"use client";

import { OVERLAY } from "../overlay";
import { Toaster } from "sonner";

/**
 * 토스트가 쌓이는 자리 (#112, #134, #291 5단계).
 *
 * **#134 가 정한 것을 그대로 지킨다** — 데스크톱은 우측 하단이다. 가운데는 눈이 가장
 * 자주 머무는 자리라 본문 위를 덮고, 결과를 알리면서 원래 내용도 계속 보여야 한다.
 * 좁은 화면에서는 좌우 여백만 두고 가로로 채운다. 우측에 붙이면 글이 서너 줄로 접힌다.
 *
 * **`unstyled` 로 둔다.** sonner 의 기본 모양을 쓰면 이 저장소의 색 토큰
 * (`ok`/`danger`/`info`)과 어긋나고, 라이트·다크가 따로 논다. 모양은 우리가 정하고
 * 자리·쌓임·손짓만 sonner 에게 맡긴다.
 *
 * 옮기면서 **얻은 것**:
 *   - 읽는 동안 사라지지 않는다 (마우스를 올리면 타이머가 멈춘다).
 *     전에는 5초가 지나면 읽던 중에도 사라졌다
 *   - 탭이 가려져 있는 동안 시간이 흐르지 않는다
 *   - 손가락으로 밀어 닫을 수 있다
 *   - 키보드로 토스트에 닿는다 (`⌥T`). 전에는 DOM 끝까지 탭해야 했다
 */
export function ToastViewport() {
  return (
    <Toaster
      position={OVERLAY.toastPosition}
      visibleToasts={OVERLAY.toastMaxVisible}
      duration={OVERLAY.toastDurationMs}
      offset={OVERLAY.toastOffset}
      mobileOffset={OVERLAY.toastOffset}
      /*
        **층도 우리가 정한다** (#578). sonner 는 자기 스타일시트에 `z-index:999999999`
        를 박아 두는데, 그 값이 크다는 이유로 결과가 맞는 것과 **층 표(#134)가 그것을
        정했다는 것**은 다른 이야기다. 표를 고쳐도 sonner 는 따라오지 않는다.

        `style` 은 sonner 가 `[data-sonner-toaster]` 에 인라인으로 싣는다 — 그래서
        주입된 스타일시트를 이긴다.
      */
      style={{ zIndex: "var(--z-index-toast)" }}
      // 모양은 우리 것이다. 색은 토큰에서 오므로 라이트·다크가 함께 따라온다.
      toastOptions={{
        unstyled: true,
        classNames: {
          toast: OVERLAY.toastItem,
          success: OVERLAY.toastTone.success,
          error: OVERLAY.toastTone.error,
          default: OVERLAY.toastTone.info,
          closeButton: "shrink-0 rounded px-1 text-ink-muted transition hover:text-ink",
        },
        style: { width: "100%", maxWidth: OVERLAY.toastWidth },
      }}
      // 긴 메시지를 다 못 읽고 사라지는 것도 문제지만, 남아서 가리는 것도 문제다.
      closeButton
    />
  );
}
