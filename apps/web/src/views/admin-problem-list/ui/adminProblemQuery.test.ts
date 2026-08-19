import { describe, expect, it } from "bun:test";
import { EMPTY_FILTERS } from "./AdminProblemFilters";
import { isFiltered, toQuery } from "./adminProblemQuery";

describe("어드민 문제 목록의 질의", () => {
  it("안 건 조건은 키째로 빠진다", () => {
    // `published=""` 를 보내면 서버가 빈 문자열을 Boolean 으로 읽다 400 을 낸다.
    // 조건을 **지우는 순간** 목록이 깨지는 종류라, 화면에서는 늦게 발견된다.
    expect(toQuery(EMPTY_FILTERS)).toEqual({ sort: "LATEST" });
  });

  it("건 조건만 담는다", () => {
    expect(toQuery({ ...EMPTY_FILTERS, q: "합", published: "false" })).toEqual({
      q: "합",
      published: "false",
      sort: "LATEST",
    });
  });

  it("published 는 false 도 값이다", () => {
    // "미공개만" 이 이 화면의 주된 쓰임이다. 문자열 "false" 가 거짓으로 취급돼
    // 빠지면 **가장 자주 쓰는 조건만 조용히 안 걸린다**.
    expect(toQuery({ ...EMPTY_FILTERS, published: "false" }).published).toBe("false");
  });

  it("아무 조건도 안 걸었으면 걸르지 않은 것이다", () => {
    expect(isFiltered(EMPTY_FILTERS)).toBe(false);
  });

  it("정렬만 바꾼 것은 조건이 아니다", () => {
    // 빈 목록이 "아직 없다" 인지 "못 찾았다" 인지를 가르는 값이다. 정렬을 바꿔서
    // 결과가 0건이 되는 일은 없으므로, 정렬을 조건으로 세면 안내가 틀린다.
    expect(isFiltered({ ...EMPTY_FILTERS, sort: "TITLE" })).toBe(false);
  });

  it("조건을 하나라도 걸면 걸른 것이다", () => {
    expect(isFiltered({ ...EMPTY_FILTERS, published: "true" })).toBe(true);
    expect(isFiltered({ ...EMPTY_FILTERS, q: "합" })).toBe(true);
  });
});
