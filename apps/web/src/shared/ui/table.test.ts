import { describe, expect, it } from "bun:test";
import { cellTarget } from "./tableLink";

/**
 * 칸이 어디로 가는지 (#197).
 *
 * **열 이름과 목적지가 어긋나던 자리다.** 제출 목록에서 "문제" 열을 눌렀는데 제출
 * 상세로 갔다 — 행 하나에 목적지가 하나뿐이었기 때문이다.
 */
describe("cellTarget", () => {
  const row = { id: 7, slug: "two-sum" };

  it("열이 자기 목적지를 가지면 그것으로 간다", () => {
    expect(cellTarget({ href: () => "/problems/two-sum" }, row, undefined)).toBe("/problems/two-sum");
  });

  it("열 목적지가 행 목적지를 이긴다", () => {
    // 더 구체적인 쪽이 이겨야 한다. 반대면 열마다 다른 곳으로 갈 수 없다.
    expect(cellTarget({ href: () => "/problems/two-sum" }, row, () => "/submissions/7")).toBe(
      "/problems/two-sum",
    );
  });

  it("열 목적지가 없으면 행 목적지를 쓴다", () => {
    expect(cellTarget({}, row, () => "/submissions/7")).toBe("/submissions/7");
  });

  it("둘 다 없으면 링크가 아니다", () => {
    expect(cellTarget({}, row, undefined)).toBeNull();
  });

  it("열이 null 을 돌려주면 그 행만 링크가 아니다", () => {
    // 같은 열이라도 행에 따라 갈 곳이 없을 수 있다.
    expect(cellTarget({ href: () => null }, row, () => "/submissions/7")).toBeNull();
  });
});
