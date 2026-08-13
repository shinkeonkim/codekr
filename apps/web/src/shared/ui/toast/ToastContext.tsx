"use client";

import { toast as sonner } from "sonner";
import type { ReactNode } from "react";

export type ToastTone = "success" | "error" | "info";

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
  dismiss: (id?: string | number) => void;
}

/**
 * 색만으로 구분하지 않는다 — 아이콘과 **소리 내어 읽히는 라벨**이 함께 있어야 한다.
 *
 * 이 저장소의 규칙이다 (잔디 그래프·멘션도 같다). 색각 이상이 있는 사람에게 초록과
 * 빨강은 같은 회색이고, 스크린 리더에게는 색이 아예 없다.
 */
const TONES: Record<ToastTone, { icon: string; label: string }> = {
  success: { icon: "✓", label: "완료" },
  error: { icon: "!", label: "오류" },
  info: { icon: "i", label: "안내" },
};

function body(tone: ToastTone, message: string): ReactNode {
  return (
    <span className="min-w-0 flex-1 break-words text-ink">
      <span className="sr-only">{TONES[tone].label}: </span>
      {message}
    </span>
  );
}

function icon(tone: ToastTone): ReactNode {
  return (
    <span aria-hidden className="mt-0.5 font-bold">
      {TONES[tone].icon}
    </span>
  );
}

/**
 * 토스트를 띄운다 (#112, #134, #291 5단계).
 *
 * **부르는 쪽은 바뀌지 않았다** — `useToast().success(…)` 그대로다. 안에서 무엇이
 * 도는지는 스물아홉 개 화면이 알 필요가 없고, 그래서 이 이관이 화면을 건드리지 않았다.
 *
 * 컨텍스트를 쓰지 않는다. sonner 의 `toast()` 는 전역 함수라 Provider 가 필요 없고,
 * **Provider 밖에서 불러도 터지지 않는다** — 전에 폴백을 따로 둬서 지키던 성질이
 * 이제 구조에서 나온다.
 */
export function useToast(): ToastApi {
  return API;
}

const API: ToastApi = {
  success: (message) => sonner.success(body("success", message), { icon: icon("success") }),
  error: (message) => sonner.error(body("error", message), { icon: icon("error") }),
  info: (message) => sonner.message(body("info", message), { icon: icon("info") }),
  dismiss: (id) => sonner.dismiss(id),
};
