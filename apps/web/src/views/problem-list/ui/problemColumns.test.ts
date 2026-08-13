import { describe, expect, it } from "bun:test";
import { PROBLEM_COLUMNS } from "./problemColumns";

/**
 * 문제 목록에서 무엇을 눌러야 문제로 가는가 (#379).
 *
 * **열 순서가 바뀌어도 링크가 따라가야 한다.** #204 가 번호 열을 맨 앞에 넣으면서
 * 링크가 제목에서 번호로 조용히 옮겨 갔던 것이 이 시험이 막는 것이다.
 */
describe("문제 목록의 링크", () => {
  const problem = { id: 42 } as Parameters<NonNullable<(typeof PROBLEM_COLUMNS)[number]["href"]>>[0];

  it("제목이 문제로 간다", () => {
    const title = PROBLEM_COLUMNS.find((column) => column.key === "title");
    expect(title?.href?.(problem)).toBe("/problems/42");
  });

  it("링크를 가진 열이 정확히 하나다", () => {
    // 여럿이면 한 행에서 탭 정지가 여러 번 생긴다. 없으면 목록에서 아무 데도 못 간다.
    expect(PROBLEM_COLUMNS.filter((column) => column.href).length).toBe(1);
  });
});
