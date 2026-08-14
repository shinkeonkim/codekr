import { describe, expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * 고정 요소의 **층** (#134, #578).
 *
 * 좁은 화면 드로어가 툴팁·`Select` 목록·멘션 목록 **아래에 깔려 있었다.** 원인은
 * 드로어 한 줄이 아니라 **층 체계 전체가 죽어 있었던 것**이다 — 토큰이 `--z-*` 로
 * 적혀 있었는데 Tailwind 가 `z-*` 유틸리티를 만들 때 보는 이름은 `--z-index-*` 다.
 * 그래서 `z-header`·`z-tooltip`·`z-modal` 은 **CSS 규칙이 만들어진 적이 없고**, 층이
 * 없으니 DOM 순서가 정했다.
 *
 * `colorTokens.test.ts` 가 잡는 것과 같은 종류다 — **없는 이름을 써도 화면은 뜬다.**
 * 다만 색은 글자색을 따라가 눈에 띄고, 층은 겹치기 전까지 아무 일도 일어나지 않는다.
 *
 * 그래서 셋을 시험한다.
 *
 * 1. **Tailwind 가 읽는 이름인가.** 이것이 틀리면 나머지는 다 무의미하다
 * 2. **순서가 표에 있는가.** 값이 뒤섞이면 "무엇이 무엇 위" 가 다시 알 수 없게 된다
 * 3. **숫자가 화면 파일에 흩어져 있지 않은가.** 드롭다운이 그랬다 — 층 표를 고쳐도
 *    그 한 줄은 따라오지 않는다
 */

const SRC = join(import.meta.dir, "..", "..");
const GLOBALS = join(SRC, "app", "globals.css");

/**
 * Tailwind v4 의 `z-*` 유틸리티가 읽는 네임스페이스.
 * `node_modules/tailwindcss` 안에 `themeKeys:["--z-index"]` 로 박혀 있다.
 */
const NAMESPACE = "--z-index-";

/** 아래에서 위로. 이 순서가 곧 "무엇이 무엇을 덮는가" 다. */
const ORDER = ["header", "tooltip", "drawer", "modal", "toast"] as const;

/** `--z-` 로 시작하는 것을 **전부** 모은다 — 네임스페이스를 틀린 것도 잡아야 한다. */
function declared(): Map<string, number> {
  const css = readFileSync(GLOBALS, "utf8");
  const found = new Map<string, number>();
  for (const match of css.matchAll(/(--z-[a-z0-9-]*)\s*:\s*(\d+)\s*;/g)) {
    found.set(match[1], Number(match[2]));
  }
  return found;
}

function layers(): Map<string, number> {
  const found = new Map<string, number>();
  for (const [name, value] of declared()) {
    if (name.startsWith(NAMESPACE)) found.set(name.slice(NAMESPACE.length), value);
  }
  return found;
}

function tsxFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...tsxFiles(path));
    else if (path.endsWith(".tsx")) out.push(path);
  }
  return out;
}

describe("고정 요소의 층 (#134, #578)", () => {
  test("Tailwind 가 읽는 이름으로 적혀 있다", () => {
    // 이것이 틀리면 클래스가 조용히 안 만들어진다 — 화면은 멀쩡히 뜬다.
    const wrong = [...declared().keys()].filter((name) => !name.startsWith(NAMESPACE));
    expect(wrong).toEqual([]);
  });

  test("표에 있는 층이 전부다", () => {
    expect([...layers().keys()].sort()).toEqual([...ORDER].sort());
  });

  test("아래에서 위로 값이 커진다", () => {
    const found = layers();
    const values = ORDER.map((name) => found.get(name) ?? -1);
    expect(values).toEqual([...values].sort((a, b) => a - b));
    // 같은 값이 둘이면 순서를 DOM 이 정하게 된다 — 그것이 이 이슈의 원인이었다.
    expect(new Set(values).size).toBe(values.length);
  });

  test("드로어는 툴팁 위, 대화상자 아래다", () => {
    const found = layers();
    // 위: 드로어가 열리면 그 아래 화면에서 뜬 것들이 보이면 안 된다 (#578).
    expect(found.get("drawer")!).toBeGreaterThan(found.get("tooltip")!);
    // 아래: 드로어 **안에서** 연 대화상자는 드로어 위에 떠야 한다.
    expect(found.get("drawer")!).toBeLessThan(found.get("modal")!);
  });

  test("화면 파일에 층 숫자를 적지 않는다", () => {
    // 숫자로 적힌 층(`z-` + 숫자, 대괄호 값, 음수)을 찾는다. 이름으로 쓴 것은 통과한다.
    // 패턴을 이어 붙여 만드는 이유: 이 파일도 Tailwind 가 훑는 자리라, 예시를 그대로
    // 적으면 **그 클래스가 실제로 CSS 에 생긴다.**
    const numbered = new RegExp(`(?<![\\w-])-?z-(\\d|${"\\["})`);
    const offenders = tsxFiles(SRC).filter((file) => numbered.test(readFileSync(file, "utf8")));
    expect(offenders.map((file) => file.replace(SRC, ""))).toEqual([]);
  });
});
