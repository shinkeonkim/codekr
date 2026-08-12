import { afterEach, describe, expect, test } from "bun:test";
import { THEME_STORAGE_KEY, isTheme, readStoredTheme, resolveTheme, storeTheme } from "./theme";

/**
 * 테마 선택의 저장과 해석 (#206).
 *
 * 여기서 지켜야 할 것은 **모르는 값을 만났을 때 지금까지와 같이 동작하는 것**이다.
 * 저장소가 막혀 있든 값이 망가졌든, 화면은 `system` 으로 떨어져야 한다.
 */

const storage = new Map<string, string>();

// bun 의 테스트 환경에는 window 가 없다. 필요한 만큼만 세운다.
function stubWindow(prefersDark: boolean, storageThrows = false) {
  (globalThis as { window?: unknown }).window = {
    localStorage: {
      getItem: (key: string) => {
        if (storageThrows) throw new Error("사생활 보호 모드");
        return storage.get(key) ?? null;
      },
      setItem: (key: string, value: string) => {
        if (storageThrows) throw new Error("저장 공간 없음");
        storage.set(key, value);
      },
      removeItem: (key: string) => storage.delete(key),
    },
    matchMedia: (query: string) => ({ matches: prefersDark && query.includes("dark") }),
  };
}

afterEach(() => {
  storage.clear();
  (globalThis as { window?: unknown }).window = undefined;
});

describe("readStoredTheme", () => {
  test("저장된 적이 없으면 시스템 따름", () => {
    stubWindow(false);
    expect(readStoredTheme()).toBe("system");
  });

  test("저장된 값을 읽는다", () => {
    stubWindow(false);
    storage.set(THEME_STORAGE_KEY, "dark");
    expect(readStoredTheme()).toBe("dark");
  });

  test("망가진 값은 시스템 따름으로 떨어진다", () => {
    // 다른 버전이 남긴 값이나 손으로 고친 값이 들어올 수 있다.
    stubWindow(false);
    storage.set(THEME_STORAGE_KEY, "sepia");
    expect(readStoredTheme()).toBe("system");
  });

  test("저장소가 던져도 화면은 살아 있어야 한다", () => {
    // 사생활 보호 모드에서는 **읽기만 해도** 예외가 난다.
    stubWindow(false, true);
    expect(readStoredTheme()).toBe("system");
  });

  test("서버에서는 알 수 없으므로 시스템 따름", () => {
    expect(readStoredTheme()).toBe("system");
  });
});

describe("storeTheme", () => {
  test("시스템 따름은 값을 지운다", () => {
    // 남겨 두면 다음에 기본값이 바뀌었을 때 옛 값이 그것을 이긴다.
    stubWindow(false);
    storage.set(THEME_STORAGE_KEY, "dark");
    storeTheme("system");
    expect(storage.has(THEME_STORAGE_KEY)).toBe(false);
  });

  test("저장에 실패해도 던지지 않는다", () => {
    stubWindow(false, true);
    expect(() => storeTheme("dark")).not.toThrow();
  });
});

describe("resolveTheme", () => {
  test("고른 값이 있으면 기기 설정을 보지 않는다", () => {
    stubWindow(true);
    expect(resolveTheme("light")).toBe("light");
    stubWindow(false);
    expect(resolveTheme("dark")).toBe("dark");
  });

  test("시스템 따름이면 기기 설정을 본다", () => {
    stubWindow(true);
    expect(resolveTheme("system")).toBe("dark");
    stubWindow(false);
    expect(resolveTheme("system")).toBe("light");
  });
});

describe("인라인 스크립트", () => {
  test("스크립트가 같은 저장 키를 쓴다", async () => {
    // 스크립트는 첫 그림을 앞지르려고 **문자열**로 들어 있어서 이 상수를 가져다 쓸 수
    // 없다. 키를 한쪽만 바꾸면 고른 테마가 조용히 무시되고, 그 증상은 "가끔 번쩍인다"
    // 라서 원인을 짚기 어렵다.
    const source = await Bun.file(
      new URL("./ThemeScript.tsx", import.meta.url).pathname,
    ).text();
    expect(source).toContain(`"${THEME_STORAGE_KEY}"`);
  });
});

describe("isTheme", () => {
  test("세 값만 받는다", () => {
    expect(isTheme("dark")).toBe(true);
    expect(isTheme("system")).toBe(true);
    expect(isTheme("sepia")).toBe(false);
    expect(isTheme(null)).toBe(false);
  });
});
