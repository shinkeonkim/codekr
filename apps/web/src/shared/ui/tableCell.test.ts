import { describe, expect, it } from "bun:test";
import { cellClass } from "./tableCell";

/**
 * 머리글과 값이 같은 규칙을 본다 (#193).
 *
 * 이 함수가 있는 이유가 곧 이 테스트의 이유다 — 전에는 `thead` 와 `tbody` 가 같은 조건식을
 * 각자 들고 있어서, 한쪽만 고치면 축이 어긋난 채로 통과했다.
 */
describe("cellClass", () => {
  it("기본은 왼쪽 — 클래스를 붙이지 않는다", () => {
    expect(cellClass({})).toBe("");
  });

  it("수치 열은 가운데", () => {
    expect(cellClass({ align: "center" })).toBe("text-center");
  });

  it("정렬과 숨김이 함께 걸린다", () => {
    expect(cellClass({ align: "center", hideBelow: "lg" })).toBe("text-center hidden lg:table-cell");
  });

  it("숨김만 걸리면 정렬 클래스는 비어 있다", () => {
    // 빈 문자열이 섞여 공백이 두 번 들어가면 안 된다.
    expect(cellClass({ hideBelow: "sm" })).toBe("hidden sm:table-cell");
  });
});
