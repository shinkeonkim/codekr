import { describe, expect, test } from "bun:test";
import { countCases } from "./regexCases";

/**
 * 확인 문자열 세기 (#653).
 *
 * **맞으면 안 되는 줄이 0이면 `.*` 가 통과하는 문제다.** 그것은 오류를 내지 않아
 * 저장한 뒤에는 알 방법이 없다 — 이 숫자가 쓰면서 알게 해 준다.
 */
describe("countCases", () => {
  test("판정 표시로 나눠 센다", () => {
    expect(countCases("+a\n+b\n-c\n")).toEqual({ positive: 2, negative: 1, malformed: 0 });
  });

  /** 파일 끝의 개행 하나로 줄이 늘면 안 된다. */
  test("빈 줄은 세지 않는다", () => {
    expect(countCases("+a\n\n\n-b\n")).toEqual({ positive: 1, negative: 1, malformed: 0 });
    expect(countCases("")).toEqual({ positive: 0, negative: 0, malformed: 0 });
  });

  test("표시가 없는 줄을 따로 센다", () => {
    expect(countCases("+a\nb\n-c\n")).toEqual({ positive: 1, negative: 1, malformed: 1 });
  });

  /** **이 경우가 이 함수의 이유다.** 화면이 이때 경고를 띄운다. */
  test("맞으면 안 되는 줄이 없는 것을 드러낸다", () => {
    expect(countCases("+a\n+b\n").negative).toBe(0);
  });
});
