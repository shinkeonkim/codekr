import { describe, expect, it } from "bun:test";
import { NAV_ITEMS, activeHref } from "./nav";

describe("activeHref", () => {
  it("겹치는 경로에서는 더 구체적인 항목 하나만 활성이다", () => {
    // 전에는 '전체 제출'과 '내 제출'이 동시에 켜졌다 (#182).
    expect(activeHref("/submissions/explore")).toBe("/submissions/explore");
    expect(activeHref("/submissions")).toBe("/submissions");
  });

  it("하위 경로도 그 항목의 활성으로 본다", () => {
    expect(activeHref("/problems/two-sum/solve")).toBe("/problems");
    expect(activeHref("/submissions/97")).toBe("/submissions");
  });

  it("접두사가 같아도 경로 구분자가 없으면 활성이 아니다", () => {
    // '/postscript' 가 '/posts' 를 켜면 안 된다.
    expect(activeHref("/postscript")).toBeNull();
  });

  it("어느 항목에도 속하지 않으면 없다", () => {
    expect(activeHref("/settings")).toBeNull();
    expect(activeHref("/")).toBeNull();
  });

  it("모든 항목이 자기 경로에서 활성이다", () => {
    NAV_ITEMS.forEach((item) => expect(activeHref(item.href)).toBe(item.href));
  });
});
