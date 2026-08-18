import { describe, expect, test } from "bun:test";
import { PRIMARY_KEYS, SECONDARY_KEYS, activeChips, hasActiveFilters } from "./filters";

describe("필터 칩 (#76)", () => {
  test("페이지는 필터가 아니라 위치라 칩이 되지 않는다", () => {
    expect(activeChips({ page: "3" })).toEqual([]);
    expect(hasActiveFilters({ page: "3" })).toBe(false);
  });

  test("정렬은 늘 값이 있어 칩이 되지 않는다", () => {
    // 칩으로 두면 지울 수 없는 칩이 하나 늘 붙어 있게 된다.
    expect(activeChips({ sort: "LATEST" })).toEqual([]);
  });

  test("접어 둔 필터도 칩으로 보인다", () => {
    // 보이지 않으면 사용자는 목록이 왜 비었는지 알 수 없다.
    expect(activeChips({ from: "2026-01-01" })).toEqual(["from"]);
  });

  test("범위가 고정된 화면에서는 그 필터를 칩으로 보이지 않는다", () => {
    // 문제 상세 안에서 "문제: two-sum" 칩은 지울 수 없는데 지울 수 있어 보인다.
    expect(activeChips({ problemKey: "two-sum", verdict: "ACCEPTED" }, ["problemKey"]))
      .toEqual(["verdict"]);
  });

  test("빈 문자열은 걸리지 않은 것이다", () => {
    expect(activeChips({ verdict: "" })).toEqual([]);
  });

  test("상시 노출과 접는 필터가 겹치지 않는다", () => {
    // 겹치면 같은 필터가 두 군데 그려진다.
    const overlap = PRIMARY_KEYS.filter((key) => SECONDARY_KEYS.includes(key));
    expect(overlap).toEqual([]);
  });
});
