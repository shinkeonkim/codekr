import { describe, expect, test } from "bun:test";
import { parseTable, splitCells } from "./markdownTable";

/**
 * 지문의 표 (#590).
 *
 * **SQL 문제는 표를 읽어야 쿼리를 쓸 수 있다** (#526 의 예시 데이터). 그 표가 파이프
 * 그대로 보이고 있었다.
 */
describe("지문의 표", () => {
  const table = ["| 키 | 지금 |", "|---|---|", "| `stock:apple` | `count 10` |", "| `stock:kiwi` | 없음 |"];

  test("머리글과 줄을 읽는다", () => {
    const parsed = parseTable(table, 0)!;

    expect(parsed.header).toEqual(["키", "지금"]);
    expect(parsed.rows).toEqual([["`stock:apple`", "`count 10`"], ["`stock:kiwi`", "없음"]]);
    expect(parsed.next).toBe(4);
  });

  test("구분선이 없으면 표가 아니다", () => {
    // 지문에 `a | b` 같은 글이 그냥 나올 수 있다. 그것을 표로 만들면 문단이 사라진다.
    expect(parseTable(["| 이건 표가 아니다 |", "그냥 다음 줄"], 0)).toBeNull();
  });

  test("파이프로 시작하지 않으면 표가 아니다", () => {
    expect(parseTable(["문단입니다", "|---|---|"], 0)).toBeNull();
  });

  test("값에 든 파이프는 칸을 자르지 않는다", () => {
    // 값에 파이프가 드는 경우가 실제로 있다 (#532 가 채점에서 겪는 그것).
    expect(splitCells("| a\\|b | c |")).toEqual(["a|b", "c"]);
  });

  test("칸이 모자란 줄은 빈 칸으로 채운다", () => {
    const parsed = parseTable(["| a | b | c |", "|---|---|---|", "| 1 |"], 0)!;

    expect(parsed.rows).toEqual([["1", "", ""]]);
  });

  test("줄 없는 표도 머리글은 보인다", () => {
    // 0행인 것과 "무엇을 고른 것인지 모르는 것" 은 다르다 (#525 에서 같은 판단).
    const parsed = parseTable(["| id | name |", "|---|---|"], 0)!;

    expect(parsed.header).toEqual(["id", "name"]);
    expect(parsed.rows).toEqual([]);
  });

  test("정렬 표기가 있어도 구분선으로 읽는다", () => {
    expect(parseTable(["| a | b |", "|:--|--:|", "| 1 | 2 |"], 0)!.rows).toEqual([["1", "2"]]);
  });
});
