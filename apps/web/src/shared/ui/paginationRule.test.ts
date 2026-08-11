import { describe, expect, it } from "bun:test";
import { paginationView } from "./paginationRule";

/**
 * 목록 아래에 무엇을 보여줄지 (#181).
 *
 * **총 건수가 사라지던 자리다.** 페이지가 하나면 통째로 아무것도 그리지 않아서, 목록이
 * 한 페이지에 다 들어간 것인지 페이지 이동이 빠진 것인지 구분되지 않았다.
 */
describe("paginationView", () => {
  it("결과가 없으면 아무것도 그리지 않는다", () => {
    expect(paginationView({ page: 0, totalPages: 0, totalElements: 0 }).visible).toBe(false);
  });

  it("한 페이지면 총 건수만 보여준다", () => {
    const view = paginationView({ page: 0, totalPages: 1, totalElements: 19 });

    expect(view.visible).toBe(true);
    expect(view.summary).toBe("총 19건");
    expect(view.showButtons).toBe(false);
  });

  it("여러 페이지면 현재 위치도 함께 보여준다", () => {
    const view = paginationView({ page: 2, totalPages: 5, totalElements: 100 });

    expect(view.summary).toBe("총 100건 · 3/5 페이지");
    expect(view.showButtons).toBe(true);
  });

  it("건수에 천 단위 구분을 넣는다", () => {
    expect(paginationView({ page: 0, totalPages: 1, totalElements: 1234 }).summary).toBe("총 1,234건");
  });
});
