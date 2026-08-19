import { describe, expect, it } from "bun:test";
import type { ProblemSummary } from "@/entities/problem";
import { adminProblemColumns } from "./adminProblemColumns";

/**
 * 어드민 목록에서 문제를 누르면 어디로 가는가 (#625).
 *
 * **사용자 목록과 목적지가 다르다.** 저쪽은 푸는 화면(`/problems/{id}`)으로 가고
 * 여기는 고치는 화면으로 간다. 같은 `ProblemSummary` 를 쓰는 열 정의가 둘이라
 * **복사해 오다가 목적지만 남는 실수**가 나오기 쉽다 — #379 가 겪은 종류다.
 */
describe("어드민 문제 목록의 링크", () => {
  const columns = adminProblemColumns(() => {});
  const problem = { id: 42 } as ProblemSummary;

  it("제목이 편집 화면으로 간다", () => {
    const title = columns.find((column) => column.key === "title");
    expect(title?.href?.(problem)).toBe("/admin/problems/42/edit");
  });

  it("링크를 가진 열이 정확히 하나다", () => {
    // 작업 열에도 "수정" 링크가 있지만 그것은 버튼이다. 칸 전체가 링크인 열이 둘이면
    // 한 행에서 탭 정지가 여러 번 생긴다.
    expect(columns.filter((column) => column.href).length).toBe(1);
  });

  it("공개 여부 열이 있다", () => {
    // 이 화면에만 있는 열이다. 사용자 목록에는 공개된 것만 오므로 열 자체가 없다.
    expect(columns.some((column) => column.key === "published")).toBe(true);
  });
});
