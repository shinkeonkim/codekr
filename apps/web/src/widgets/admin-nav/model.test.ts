import { describe, expect, test } from "bun:test";
import { ADMIN_NAV, visibleNav } from "./model";

describe("어드민 구획 (#131)", () => {
  test("문제 출제자에게 인프라 항목을 보여주지 않는다", () => {
    // 눌렀을 때 403 이 나는 링크는 고장으로 보인다.
    const items = visibleNav(["USER", "PROBLEM_SETTER"]).map((it) => it.href);

    expect(items).toContain("/admin/problems");
    expect(items).not.toContain("/admin/queues");
  });

  test("최고 관리자는 전부 본다", () => {
    // 위계는 서버의 RoleHierarchy 와 같은 모양이어야 한다.
    expect(visibleNav(["SUPERUSER"]).length).toBe(ADMIN_NAV.length);
  });

  test("운영 관리자는 출제자 항목도 본다", () => {
    expect(visibleNav(["ADMIN"]).map((it) => it.href)).toContain("/admin/problems");
  });

  test("일반 회원에게는 아무 항목도 없다", () => {
    expect(visibleNav(["USER"])).toEqual([]);
  });

  test("모든 항목에 설명이 있다", () => {
    // 라벨만으로는 무엇을 하는 화면인지 모르는 것들이 생긴다.
    expect(ADMIN_NAV.every((it) => it.description.length > 0)).toBe(true);
  });
});
