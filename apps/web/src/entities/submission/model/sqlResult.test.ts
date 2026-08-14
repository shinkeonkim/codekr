import { describe, expect, test } from "bun:test";
import { parseSqlResult } from "./sqlResult";

/**
 * SQL 실행 결과를 표로 읽기 (#525).
 *
 * **표를 다루는 문제인데 결과가 표가 아니었다.** 여기서 확인하는 것은 값에 든 구분자가
 * 표를 무너뜨리지 않는가와, NULL 이 빈 값과 갈리는가다.
 */
describe("SQL 실행 결과", () => {
  test("첫 줄이 열 이름이 된다", () => {
    const table = parseSqlResult("id,name,city\n1,김철수,서울\n");
    expect(table?.columns).toEqual(["id", "name", "city"]);
    expect(table?.rows).toEqual([["1", "김철수", "서울"]]);
  });

  test("값에 든 쉼표는 칸을 자르지 않는다", () => {
    // 예전 형식(`-F'|'`)이 정확히 여기서 무너졌다 — 값과 구분자를 가릴 수 없었다.
    const table = parseSqlResult('id,note\n1,"쉼표,있음"\n');
    expect(table?.rows).toEqual([["1", "쉼표,있음"]]);
  });

  test("값에 든 따옴표는 두 번 적힌 것을 하나로 읽는다", () => {
    const table = parseSqlResult('id,note\n1,"따옴표""있음"\n');
    expect(table?.rows).toEqual([["1", '따옴표"있음']]);
  });

  test("값에 든 줄바꿈은 줄을 자르지 않는다", () => {
    // 줄 단위로 먼저 자르면 여기서 행이 둘로 갈린다.
    const table = parseSqlResult('id,note\n1,"첫 줄\n둘째 줄"\n');
    expect(table?.rows).toEqual([["1", "첫 줄\n둘째 줄"]]);
  });

  test("NULL 과 빈 문자열을 가른다", () => {
    // **CSV 에는 NULL 이 없다.** 하네스가 `∅` 로 적어 보내고 여기서 되돌린다.
    const table = parseSqlResult("id,note\n1,∅\n2,\n");
    expect(table?.rows).toEqual([
      ["1", null],
      ["2", ""],
    ]);
  });

  test("결과 집합이 없으면 표가 아니다", () => {
    // UPDATE·INSERT 는 낼 것이 없다 (#453). 억지로 표를 만들면 없는 구조를 지어낸다.
    expect(parseSqlResult("")).toBeNull();
    expect(parseSqlResult("   \n")).toBeNull();
  });

  test("행이 없어도 열 이름은 보인다", () => {
    // 0행인 것과 "무엇을 고른 것인지 모르는 것" 은 다르다.
    const table = parseSqlResult("id,name\n");
    expect(table?.columns).toEqual(["id", "name"]);
    expect(table?.rows).toEqual([]);
  });
});
