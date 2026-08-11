"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";

export type ToastTone = "success" | "error" | "info";

export interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
  dismiss: (id: number) => void;
}

const ToastStateContext = createContext<Toast[]>([]);
const ToastApiContext = createContext<ToastApi | null>(null);

/** 한 번에 쌓아 둘 최대 개수. 넘치면 오래된 것부터 밀어낸다. */
const MAX_VISIBLE = 3;

/** 자동으로 사라지기까지의 시간. 닫기 버튼이 있으므로 너무 길게 두지 않는다. */
const AUTO_DISMISS_MS = 5_000;

/**
 * 토스트 보관소 (#112).
 *
 * 상태와 조작을 **두 컨텍스트로 나눈** 이유: 토스트를 띄우기만 하는 화면이 목록이 바뀔
 * 때마다 다시 그려질 이유가 없다. 조작 컨텍스트의 값은 바뀌지 않는다.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((previous) => previous.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = nextId.current++;
      setToasts((previous) => [...previous, { id, tone, message }].slice(-MAX_VISIBLE));
      // 이미 사라진 id 를 지우는 것은 무해하므로 타이머를 따로 정리하지 않는다.
      setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (message) => push("success", message),
      error: (message) => push("error", message),
      info: (message) => push("info", message),
      dismiss,
    }),
    [push, dismiss],
  );

  return (
    <ToastApiContext.Provider value={api}>
      <ToastStateContext.Provider value={toasts}>{children}</ToastStateContext.Provider>
    </ToastApiContext.Provider>
  );
}

/**
 * 토스트를 띄운다.
 *
 * Provider 밖에서 불러도 **터지지 않는다.** 알림은 부가 기능이라 그것 때문에 화면이
 * 통째로 죽으면 안 된다. 대신 콘솔에 남겨 개발 중에 드러나게 한다.
 */
export function useToast(): ToastApi {
  return useContext(ToastApiContext) ?? FALLBACK;
}

export function useToastList(): Toast[] {
  return useContext(ToastStateContext);
}

const FALLBACK: ToastApi = {
  success: (message) => console.warn("[toast] Provider 밖에서 호출됨:", message),
  error: (message) => console.warn("[toast] Provider 밖에서 호출됨:", message),
  info: (message) => console.warn("[toast] Provider 밖에서 호출됨:", message),
  dismiss: () => undefined,
};
