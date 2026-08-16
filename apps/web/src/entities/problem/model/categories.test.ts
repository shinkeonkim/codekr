import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { describe, expect, it } from "bun:test";
import { CATEGORY_LABELS } from "./labels";

/**
 * 분야 목록이 서버와 어긋나지 않는지 (#592).
 *
 * **두 곳이 같은 목록을 든다.** 서버가 `ProblemCategory` 를 늘렸는데 화면이 따라오지
 * 않으면, 그 분야의 문제는 목록에서 **이름 없는 빈칸**이 되고 거르개에도 안 뜬다.
 * 반대로 화면에만 있으면 고를 수는 있는데 서버가 400 을 준다.
 *
 * 타입(`Record<ProblemCategory, string>`)은 **화면 안쪽만** 맞춰 준다 — 그 union 자체가
 * 서버를 베껴 적은 것이라, 베낄 때 빠뜨리면 아무도 알려주지 않는다.
 * 색 토큰(#286)·층 토큰(#578)이 같은 방식으로 갈라졌고 같은 방식으로 막았다.
 */
const ENUM_PATH = "apps/api/src/main/kotlin/codekr/api/problem/entity/ProblemCategory.kt";

function serverCategories(): string[] {
  // 작업 디렉터리가 어디든 저장소 뿌리를 찾아 올라간다.
  let directory = process.cwd();
  while (!existsSync(join(directory, ENUM_PATH))) {
    const parent = dirname(directory);
    if (parent === directory) throw new Error(`${ENUM_PATH} 를 찾지 못했습니다`);
    directory = parent;
  }

  const source = readFileSync(join(directory, ENUM_PATH), "utf8");
  const body = source.slice(source.indexOf("enum class ProblemCategory"));
  // `ALGORITHM("알고리즘"),` 꼴만 센다. 주석 안의 이름은 들여쓰기가 `*` 로 시작해 걸리지 않는다.
  return [...body.matchAll(/^ {4}([A-Z_]+)\("/gm)].map((match) => match[1]);
}

describe("문제 분야", () => {
  it("서버가 아는 분야를 화면도 전부 안다", () => {
    const missing = serverCategories().filter((category) => !(category in CATEGORY_LABELS));

    expect(missing).toEqual([]);
  });

  it("화면에만 있는 분야는 없다", () => {
    const server = new Set(serverCategories());
    const extra = Object.keys(CATEGORY_LABELS).filter((category) => !server.has(category));

    expect(extra).toEqual([]);
  });

  it("이름이 비어 있지 않다", () => {
    // 빈 이름이면 화면에서 빈칸이 된다 — 목록에 있는 것과 다를 바 없다.
    expect(Object.values(CATEGORY_LABELS).every((label) => label.trim().length > 0)).toBe(true);
  });
});
