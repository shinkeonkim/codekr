import { describe, expect, it } from "bun:test";
import { headerState } from "./adminProblemColumns";

/**
 * 머리글 체크박스가 무엇을 말하는가 (#627).
 *
 * **"이 장" 이 기준이다.** 고른 개수만 세면, 1장에서 스무 개를 고르고 2장으로 넘어갔을
 * 때 이 장에서는 아무것도 안 골랐는데 "전부 골라짐" 으로 보인다.
 */
describe("전체 선택 상태", () => {
  const selection = (ids: number[], pageIds: number[]) => ({
    ids: new Set(ids),
    pageIds,
    onToggle: () => {},
    onToggleAll: () => {},
  });

  it("아무것도 안 골랐으면 꺼져 있다", () => {
    expect(headerState(selection([], [1, 2, 3]))).toBe(false);
  });

  it("이 장을 전부 골랐으면 켜져 있다", () => {
    expect(headerState(selection([1, 2, 3], [1, 2, 3]))).toBe(true);
  });

  it("일부만 골랐으면 중간이다", () => {
    expect(headerState(selection([2], [1, 2, 3]))).toBe("indeterminate");
  });

  it("다른 장에서 고른 것은 이 장의 상태를 바꾸지 않는다", () => {
    // 고른 개수(3)가 이 장의 수(3)와 같지만, **이 장의 것이 아니다.**
    expect(headerState(selection([7, 8, 9], [1, 2, 3]))).toBe(false);
  });
});
