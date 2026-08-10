"use client";

import { OVERLAY } from "../overlay";
import { useToast, useToastList } from "./ToastContext";
import type { ToastTone } from "./ToastContext";

/** 색만으로 구분하지 않는다 — 아이콘과 라벨이 함께 있어야 한다. */
const TONES: Record<ToastTone, { className: string; icon: string; label: string }> = {
  success: { className: "border-ok/40 bg-ok/12 text-ok", icon: "✓", label: "완료" },
  error: { className: "border-danger/40 bg-danger/12 text-danger", icon: "!", label: "오류" },
  info: { className: "border-info/40 bg-info/12 text-info", icon: "i", label: "안내" },
};

/**
 * 토스트가 쌓이는 자리 (#112, #134).
 *
 * `aria-live="polite"` 로 스크린 리더에 전달한다. `assertive` 를 쓰지 않는 이유는
 * 읽고 있던 내용을 끊기 때문이다 — 토스트는 대개 그만큼 급하지 않다.
 *
 * **위치를 바꿔도 DOM 순서는 그대로 둔다** (#134). 읽는 순서는 시각적 위치가 아니라
 * DOM 이 정하므로, 옮기면 스크린 리더가 듣는 순서가 달라진다.
 *
 * 새 토스트는 **아래에 붙는다.** 위에서 밀어 올리면 읽던 토스트가 움직이고,
 * 우측 하단에서는 가장 아래가 가장 최근이라는 것이 자연스럽다.
 */
export function ToastViewport() {
  const toasts = useToastList();
  const { dismiss } = useToast();

  return (
    <div
      className={OVERLAY.toastViewport}
      role="status"
      aria-live="polite"
    >
      {toasts.map((toast) => {
        const tone = TONES[toast.tone];
        return (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-start gap-3 rounded-lg border px-4 py-3 text-sm shadow-lg backdrop-blur ${OVERLAY.toastItem} ${tone.className}`}
          >
            <span aria-hidden className="mt-0.5 font-bold">
              {tone.icon}
            </span>
            <span className="min-w-0 flex-1 break-words text-ink">
              <span className="sr-only">{tone.label}: </span>
              {toast.message}
            </span>
            {/* 긴 메시지를 다 못 읽고 사라지는 것도 문제지만, 남아서 가리는 것도 문제다. */}
            <button
              type="button"
              onClick={() => dismiss(toast.id)}
              className="shrink-0 rounded px-1 text-ink-muted transition hover:text-ink"
              aria-label="알림 닫기"
            >
              ✕
            </button>
          </div>
        );
      })}
    </div>
  );
}
