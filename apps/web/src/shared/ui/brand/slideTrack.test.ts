import { describe, expect, test } from "bun:test";
import { buildTrack, dotIndex, settleTarget } from "./slideTrack";

/**
 * 되감기지 않는 트랙 (#523).
 *
 * **눈으로만 확인할 수 있는 것이었다.** 마지막 → 첫 장이 되감기는지는 자리 계산이
 * 정하는데, 그 셈이 컴포넌트 안에 있으면 브라우저를 띄워야만 알 수 있다.
 */
describe("슬라이드 트랙", () => {
  const names = ["a", "b", "c"] as const;

  test("양 끝에 사본을 한 장씩 덧댄다", () => {
    // [c'] [a] [b] [c] [a'] — 어느 방향으로 나가도 다음 장이 이미 놓여 있다.
    expect(buildTrack(names)).toEqual(["c", "a", "b", "c", "a"]);
  });

  test("한 장뿐이면 덧대지 않는다", () => {
    // 넘길 곳이 없다. 사본을 두면 없는 장을 넘길 수 있는 것처럼 보인다.
    expect(buildTrack(["a"])).toEqual(["a"]);
  });

  test("뒤쪽 사본에 닿으면 첫 장 자리로 옮긴다", () => {
    expect(settleTarget(4, 3)).toBe(1);
  });

  test("앞쪽 사본에 닿으면 마지막 장 자리로 옮긴다", () => {
    // **이전으로 넘길 때도 되감기지 않는다** — 앞쪽 사본이 그것을 맡는다.
    expect(settleTarget(0, 3)).toBe(3);
  });

  test("진짜 자리에서는 옮기지 않는다", () => {
    // 멀쩡한 자리에서 옮기면 화면이 튄다.
    for (const position of [1, 2, 3]) {
      expect(settleTarget(position, 3)).toBeNull();
    }
  });

  test("사본 위에서도 점은 그것이 흉내 내는 장을 가리킨다", () => {
    // 사본 위에서 점이 꺼지면, 넘기는 순간 어디쯤인지 알 수 없게 된다.
    expect(dotIndex(0, 3)).toBe(2); // 앞쪽 사본 = 마지막 장
    expect(dotIndex(1, 3)).toBe(0);
    expect(dotIndex(3, 3)).toBe(2);
    expect(dotIndex(4, 3)).toBe(0); // 뒤쪽 사본 = 첫 장
  });
});
