"use client";

import { useSyncExternalStore } from "react";
import { applyTheme, readStoredTheme, resolveTheme, storeTheme } from "./theme";
import type { Theme } from "./theme";

/**
 * 테마 상태 (#206).
 *
 * **`useEffect` 로 상태를 맞추지 않는다.** 그렇게 하면 서버가 그린 값으로 한 번 렌더한
 * 뒤 저장된 값으로 다시 렌더하게 되고(그래서 lint 도 막는다), 무엇보다 이 값은 리액트
 * 바깥(localStorage·OS 설정)에 있다. 바깥 저장소를 읽는 방법은 `useSyncExternalStore`
 * 다 — 서버 스냅샷을 따로 줄 수 있어 hydration 이 어긋나지 않는다.
 *
 * 첫 그림은 여기가 아니라 `<head>` 의 인라인 스크립트가 맡는다. 이 훅이 하는 일은
 * **바꿀 수 있게 하는 것**과 바깥의 변화를 따라가는 것이다.
 */

/** 스냅샷은 문자열 하나로 만든다 — 객체를 돌려주면 매번 새 참조가 되어 무한히 다시 그린다. */
type Snapshot = `${Theme}|light` | `${Theme}|dark`;

const SERVER_SNAPSHOT: Snapshot = "system|light";

const listeners = new Set<() => void>();
let snapshot: Snapshot | null = null;

function compute(): Snapshot {
  const theme = readStoredTheme();
  return `${theme}|${resolveTheme(theme)}` as Snapshot;
}

function refresh(): void {
  snapshot = compute();
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);

  // OS 설정이 도중에 바뀌는 것을 따라간다 — "시스템 따름" 이 절반만 참이 되지 않게.
  const media = window.matchMedia("(prefers-color-scheme: dark)");
  media.addEventListener("change", refresh);
  // 다른 탭에서 바꾼 것도 따라간다. 같은 사이트를 두 탭에 띄워 두는 일은 흔하다.
  window.addEventListener("storage", refresh);

  return () => {
    listeners.delete(listener);
    media.removeEventListener("change", refresh);
    window.removeEventListener("storage", refresh);
  };
}

function getSnapshot(): Snapshot {
  if (snapshot === null) snapshot = compute();
  return snapshot;
}

function getServerSnapshot(): Snapshot {
  // 서버는 고른 값을 알 수 없다. 저장된 것이 없을 때와 같은 상태로 그린다.
  return SERVER_SNAPSHOT;
}

export function setTheme(theme: Theme): void {
  applyTheme(theme);
  storeTheme(theme);
  refresh();
}

export function useTheme(): {
  /** 사용자가 고른 값. `system` 일 수 있다. */
  theme: Theme;
  /** 실제로 그려지는 값. 자기 색을 직접 정하는 컴포넌트가 쓴다. */
  resolved: "light" | "dark";
  setTheme: (theme: Theme) => void;
} {
  const value = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const [theme, resolved] = value.split("|") as [Theme, "light" | "dark"];
  return { theme, resolved, setTheme };
}
