import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { describe, expect, it } from "bun:test";
import { PAGE_WIDTH } from "./pageWidth";

/**
 * 페이지가 자기 폭을 손으로 정하지 않는지 (#599).
 *
 * **`mx-auto` 와 `max-w-*xl` 이 한 className 에 같이 있으면 그것은 페이지 폭이다.**
 * 셸이 이미 폭을 주는데 그 안에서 다시 좁히면, 페이지마다 값이 달라져도 아무도
 * 알려주지 않는다 — 실제로 설정은 `2xl`, 질문 작성은 `3xl`, 알림은 `3xl` 이었다.
 *
 * `max-w-md` 는 세지 않는다. 문단 폭을 잡는 데 흔히 쓰여(홈 화면) 페이지 폭과 구별이
 * 안 되기 때문이다. 폼 페이지는 `PAGE_WIDTH.form` 을 쓰므로 규칙 안에 남는다.
 */
const ROOT = (() => {
  let directory = dirname(new URL(import.meta.url).pathname);
  while (!readdirSync(directory).includes("package.json")) directory = dirname(directory);
  return join(directory, "src");
})();

/** 폭을 정하는 곳 자신과, 셸이 기본값을 적는 곳은 규칙 밖이다. */
const ALLOWED = ["shared/ui/pageWidth.ts", "shared/ui/pageWidth.test.ts", "widgets/app-shell/AppShell.tsx"];

const PAGE_WIDTH_CLASS = /className=\{?"[^"]*\bmx-auto\b[^"]*\bmax-w-[2-7]xl\b/;

function sourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.tsx?$/.test(name) ? [path] : [];
  });
}

describe("페이지 가로 폭", () => {
  it("페이지가 자기 폭을 손으로 정하지 않는다", () => {
    const offenders = sourceFiles(ROOT)
      .map((path) => ({ path: path.slice(ROOT.length + 1), source: readFileSync(path, "utf8") }))
      .filter(({ path }) => !ALLOWED.includes(path))
      .filter(({ source }) => PAGE_WIDTH_CLASS.test(source))
      .map(({ path }) => path);

    expect(offenders).toEqual([]);
  });

  it("wide 는 셸이 준 폭을 그대로 쓴다", () => {
    // 여기에 값을 적으면 셸의 폭을 바꿀 때 두 곳을 고쳐야 한다.
    expect(PAGE_WIDTH.wide).toBe("");
  });
});
