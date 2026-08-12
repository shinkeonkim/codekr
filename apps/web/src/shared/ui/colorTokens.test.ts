import { describe, expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * 없는 색 이름을 쓰지 않는다 (#286).
 *
 * Tailwind 는 **정의되지 않은 색 이름을 조용히 무시한다** — 클래스 자체를 만들지 않는다.
 * `border-line` 처럼 적으면 `border` 의 폭 1px 만 남고 색은 CSS 초깃값인
 * `currentColor` 가 된다. 즉 **테두리가 글자색을 따라간다.**
 *
 * 오타도, 없는 토큰도 화면이 뜨는 데는 지장이 없어서 **눈으로 보기 전까지 아무도
 * 모른다.** 실제로 다섯 곳에서 `line` 을 쓰고 있었고, 랭킹 화면의 테두리가 본문
 * 글자만큼 진해진 뒤에야 드러났다.
 */

const SRC = join(import.meta.dir, "..", "..");
const GLOBALS = join(SRC, "app", "globals.css");

/** 색 자리에 오는 접두사. `divide-y` 처럼 색이 아닌 값도 오므로 아래에서 걸러 낸다. */
const PREFIXES = ["text", "bg", "border", "divide", "ring", "fill", "stroke", "outline", "accent"];

/**
 * 색이 아닌 값들. Tailwind 가 같은 접두사에 크기·방향·모양도 싣는다.
 *
 * **여기에 없는 이름은 색으로 본다.** 새 유틸이 걸리면 이 목록에 더하면 되고,
 * 그 순간 "이것이 색인가" 를 한 번 묻게 되는 것이 이 시험의 값이다.
 */
const NOT_COLORS = new Set([
  // 방향·폭
  "t", "r", "b", "l", "x", "y", "s", "e", "0", "2", "4", "8",
  // 테두리 모양
  "solid", "dashed", "dotted", "double", "hidden", "none", "separate", "collapse", "spacing",
  // 글자
  "xs", "sm", "base", "lg", "xl", "left", "right", "center", "justify", "start", "end",
  "wrap", "nowrap", "balance", "pretty", "ellipsis", "clip", "transparent",
  // Tailwind 기본 키워드 색 (토큰이 아니어도 늘 있다)
  "white", "black", "current", "inherit",
]);

function definedColors(): Set<string> {
  const css = readFileSync(GLOBALS, "utf8");
  const names = new Set<string>();
  for (const match of css.matchAll(/--color-([a-z][a-z0-9-]*)\s*:/g)) names.add(match[1]);
  return names;
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

describe("색 토큰", () => {
  test("화면이 쓰는 색 이름이 모두 정의되어 있다", () => {
    const defined = definedColors();
    const unknown = new Map<string, string[]>();

    for (const file of tsxFiles(SRC)) {
      const source = readFileSync(file, "utf8");
      for (const match of source.matchAll(
        new RegExp(`\\b(?:${PREFIXES.join("|")})-([a-z][a-z0-9-]*)\\b`, "g"),
      )) {
        // `border-l-brand` 처럼 변을 지정한 것은 변 이름을 떼고 색만 본다.
        const name = match[1].replace(/^(?:t|r|b|l|x|y|s|e)-/, "");
        // 숫자가 붙은 것은 Tailwind 기본 팔레트다 (`text-amber-400`).
        if (/\d/.test(name) || NOT_COLORS.has(name)) continue;
        // 접두사가 겹치는 유틸 (`border-collapse` 는 위에서 걸렀고, 여기는 복합 토큰).
        if (defined.has(name)) continue;
        const where = unknown.get(name) ?? [];
        where.push(file.replace(SRC, ""));
        unknown.set(name, where);
      }
    }

    expect(
      [...unknown].map(([name, files]) => `${name} (${files[0]} 외 ${files.length - 1}곳)`),
    ).toEqual([]);
  });
});
