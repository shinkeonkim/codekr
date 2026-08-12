/**
 * 화면 테마 (#206).
 *
 * **`system` 이 기본값이다.** 고르지 않은 사람에게는 지금까지와 똑같이 동작해야 한다 —
 * 새 설정이 생겼다는 이유로 화면이 바뀌면, 바꾼 적 없는 사람이 고장으로 받아들인다.
 */
export type Theme = "light" | "dark" | "system";

export const THEME_LABELS: Record<Theme, { label: string; hint: string }> = {
  system: { label: "시스템 따름", hint: "기기의 설정을 그대로 따릅니다" },
  light: { label: "밝게", hint: "기기가 어둡게 설정되어 있어도 밝게 봅니다" },
  dark: { label: "어둡게", hint: "기기가 밝게 설정되어 있어도 어둡게 봅니다" },
};

/**
 * 저장 키. **`<html>` 에 테마를 바르는 인라인 스크립트도 같은 문자열을 쓴다.**
 * 그쪽은 번들 밖(문자열 안)이라 이 상수를 가져다 쓸 수 없으니, 바꾸려면 두 곳을 함께 본다.
 */
export const THEME_STORAGE_KEY = "codekr:theme";

export function isTheme(value: unknown): value is Theme {
  return value === "light" || value === "dark" || value === "system";
}

/**
 * 저장된 선택을 읽는다. 서버에서는 알 수 없으므로 `system` 이다.
 *
 * localStorage 는 사생활 보호 모드나 저장 공간이 꽉 찬 경우에 **읽기만 해도 던진다.**
 * 테마를 못 읽었다고 화면이 죽으면 안 된다.
 */
export function readStoredTheme(): Theme {
  if (typeof window === "undefined") return "system";
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isTheme(stored) ? stored : "system";
  } catch {
    return "system";
  }
}

export function storeTheme(theme: Theme): void {
  try {
    if (theme === "system") window.localStorage.removeItem(THEME_STORAGE_KEY);
    else window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // 저장에 실패해도 이번 방문 동안은 적용된다. 다음 방문에 잊힐 뿐이다.
  }
}

/**
 * 실제로 그려질 테마. `system` 이면 기기 설정을 본다.
 *
 * 이 값이 필요한 곳은 **자기 색을 직접 정하는 것들**이다 — 지금은 코드 에디터가 그렇다.
 * CSS 토큰을 쓰는 화면은 이것을 알 필요가 없다.
 */
export function resolveTheme(theme: Theme): "light" | "dark" {
  if (theme !== "system") return theme;
  if (typeof window === "undefined") return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/** `<html>` 에 선택을 바른다. `system` 이면 속성을 지워 CSS 가 OS 를 따르게 둔다. */
export function applyTheme(theme: Theme): void {
  const root = document.documentElement;
  if (theme === "system") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", theme);
}
