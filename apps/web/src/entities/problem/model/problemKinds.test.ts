import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { SELECTABLE_KINDS } from "./types";

/**
 * 고를 수 있는 채점 방식이 서버와 같은지 (#651).
 *
 * **이 목록은 사본이고, 실제로 다섯 번 어긋나 있었다.** 인터랙티브(#474)·퀴즈(#650)·
 * 정규식(#653)·Git(#654)이 서버에서는 열렸는데 여기에 없었다. 그러면
 * **어드민이 그 유형을 고를 수 없고** 문제 목록에서 걸러 볼 수도 없다 —
 * 만들어 두고 닿지 못하는 상태다.
 *
 * 그리고 그것은 **조용하다.** 서버는 잘 돌고 화면은 오류를 내지 않는다.
 * 그래서 사람이 눈으로 맞추는 것에 기대지 않고 원본을 읽어 견준다 —
 * `layers.test.ts`(#578)·`colorTokens.test.ts` 와 같은 방식이다.
 */
const KIND_SOURCE = join(
  import.meta.dir,
  "..", "..", "..", "..", "..",
  "api", "src", "main", "kotlin", "codekr", "api", "problem", "entity", "ProblemKind.kt",
);

/** `ready = true` 로 선언된 유형 이름들. */
function readyKinds(): string[] {
  const source = readFileSync(KIND_SOURCE, "utf8");
  const found: string[] = [];
  for (const match of source.matchAll(/^\s{4}([A-Z_]+)\(([^\n]*)$/gm)) {
    const [, name, rest] = match;
    if (rest.includes("ready = true")) found.push(name);
  }
  return found;
}

describe("고를 수 있는 채점 방식 (#651)", () => {
  test("원본을 읽을 수 있다", () => {
    // 경로가 틀리면 아래 시험이 **빈 목록을 견주며 통과한다** — 그것이 가장 나쁘다.
    expect(readyKinds().length).toBeGreaterThan(5);
  });

  test("서버가 연 유형이 전부 목록에 있다", () => {
    const missing = readyKinds().filter((kind) => !(kind in SELECTABLE_KINDS));
    expect(missing).toEqual([]);
  });

  test("서버가 안 연 유형은 목록에 없다", () => {
    // 고를 수는 있는데 저장하면 서버가 거절하는 상태를 막는다.
    const ready = new Set(readyKinds());
    expect(Object.keys(SELECTABLE_KINDS).filter((kind) => !ready.has(kind))).toEqual([]);
  });

  test("모든 항목에 이름이 있다", () => {
    expect(Object.values(SELECTABLE_KINDS).filter((label) => !label.trim())).toEqual([]);
  });
});
