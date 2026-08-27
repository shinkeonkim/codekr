import { describe, expect, test } from "bun:test";
import { isAnswerEmpty, toggleChoice } from "./selection";

/**
 * 보기 고르기 (#650).
 *
 * **여기가 틀리면 조용히 틀린다.** 하나만 고르는 문제에서 둘이 담기면 서버는
 * "정확히 일치" 로 채점하므로 **정답을 골라도 틀린다** — 오류는 나지 않고 사용자는
 * 자기가 틀린 줄 안다.
 */
describe("toggleChoice", () => {
  test("하나만 고르는 문제는 바꿔 끼운다", () => {
    expect(toggleChoice([], 2, true)).toEqual([2]);
    expect(toggleChoice([2], 3, true)).toEqual([3]);
    // 같은 것을 다시 눌러도 남는다 — 아무것도 안 고른 상태로 돌아가지 않는다.
    expect(toggleChoice([2], 2, true)).toEqual([2]);
  });

  test("여럿 고르는 문제는 쌓고 뺀다", () => {
    expect(toggleChoice([], 1, false)).toEqual([1]);
    expect(toggleChoice([1], 3, false)).toEqual([1, 3]);
    expect(toggleChoice([1, 3], 1, false)).toEqual([3]);
  });
});

describe("isAnswerEmpty", () => {
  test("객관식은 고른 것이 없으면 빈 것이다", () => {
    expect(isAnswerEmpty([], "", false)).toBe(true);
    expect(isAnswerEmpty([2], "", false)).toBe(false);
  });

  test("단답은 공백뿐이어도 빈 것이다", () => {
    expect(isAnswerEmpty([], "   ", true)).toBe(true);
    expect(isAnswerEmpty([], "TCP", true)).toBe(false);
  });

  /** 유형이 바뀌어도 **그 유형이 쓰는 칸만** 본다. */
  test("단답 문제에서 고른 보기는 세지 않는다", () => {
    expect(isAnswerEmpty([1, 2], "", true)).toBe(true);
  });
});
