import { describe, expect, it } from "bun:test";
import { cellTarget } from "./tableLink";

/**
 * 칸이 어디로 가는지 (#197, #379).
 *
 * **열 이름과 목적지가 어긋나던 자리다.** 제출 목록에서 "문제" 열을 눌렀는데 제출
 * 상세로 갔고(#197), 문제 목록에서는 **제목을 눌러도 아무 일이 없었다**(#379).
 * 둘 다 "행 하나에 목적지가 하나" 라는 규칙에서 나왔다.
 */
describe("cellTarget", () => {
  const row = { id: 7, slug: "two-sum" };

  it("열이 자기 목적지를 가지면 그것으로 간다", () => {
    expect(cellTarget({ href: () => "/problems/two-sum" }, row)).toBe("/problems/two-sum");
  });

  it("목적지가 없는 열은 링크가 아니다", () => {
    expect(cellTarget({}, row)).toBeNull();
  });

  it("열이 그 행에서 null 을 돌려주면 링크가 아니다", () => {
    // 지워진 문제를 가리키는 제출처럼, 갈 곳이 없는 행이 있다.
    expect(cellTarget({ href: () => null }, row)).toBeNull();
  });
});
