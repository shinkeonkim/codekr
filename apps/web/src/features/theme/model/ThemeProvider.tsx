"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { applyTheme, readStoredTheme, resolveTheme, storeTheme } from "./theme";
import type { Theme } from "./theme";

interface ThemeContextValue {
  /** 사용자가 고른 값. `system` 일 수 있다. */
  theme: Theme;
  /** 실제로 그려지는 값. 자기 색을 직접 정하는 컴포넌트가 쓴다. */
  resolved: "light" | "dark";
  setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextValue>({
  theme: "system",
  resolved: "light",
  setTheme: () => {},
});

/**
 * 테마 상태 (#206).
 *
 * **첫 그림은 이 컴포넌트가 그리지 않는다.** `<head>` 의 인라인 스크립트가 이미
 * `<html>` 에 발라 두었다 — 여기서 처음 바르면 밝은 화면이 한 번 번쩍인다.
 * 여기서 하는 일은 **바꿀 수 있게 하는 것**과, `system` 일 때 OS 설정이 도중에
 * 바뀌는 것을 따라가는 것뿐이다.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>("system");
  const [resolved, setResolved] = useState<"light" | "dark">("light");

  useEffect(() => {
    const stored = readStoredTheme();
    setThemeState(stored);
    setResolved(resolveTheme(stored));
  }, []);

  // `system` 을 고른 사람은 OS 설정을 **지금** 바꿔도 따라가야 한다. 새로고침해야
  // 반영되면 "시스템 따름" 이라는 말이 절반만 참이 된다.
  useEffect(() => {
    if (theme !== "system") return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const follow = () => setResolved(media.matches ? "dark" : "light");
    media.addEventListener("change", follow);
    return () => media.removeEventListener("change", follow);
  }, [theme]);

  const setTheme = useCallback((next: Theme) => {
    setThemeState(next);
    setResolved(resolveTheme(next));
    applyTheme(next);
    storeTheme(next);
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, resolved, setTheme }}>{children}</ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
