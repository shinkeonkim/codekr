import { describe, expect, test } from "bun:test";
import { render, screen, within } from "@testing-library/react";
import { Table } from "./Table";
import type { Column } from "./Table";

/**
 * 목록 테이블 (#79, #646).
 *
 * **여기가 첫 렌더링 시험이다.** 지금까지 웹 시험 184개는 전부 순수 함수였고,
 * `Table` 은 0% 였다 — 목록 화면 전부가 이것을 쓰는데도.
 *
 * `tableCell.test.ts`·`tableLink.test.ts` 가 이미 있는 것과 무엇이 다른가:
 * 저쪽은 **클래스 문자열과 목적지 계산**을 본다. 여기는 그것이 실제로
 * **어느 칸에 붙는지**를 본다 — #379 가 난 자리가 정확히 그 사이다.
 */

interface Row {
  id: number;
  title: string;
  tier: string;
}

const ROWS: Row[] = [
  { id: 1000, title: "A+B", tier: "브론즈 5" },
  { id: 1001, title: "A-B", tier: "브론즈 5" },
];

function columns(overrides: Partial<Column<Row>>[] = []): Column<Row>[] {
  const base: Column<Row>[] = [
    { key: "id", header: "번호", width: "w-20", render: (row) => row.id },
    { key: "title", header: "문제", href: (row) => `/problems/${row.id}`, render: (row) => row.title },
    { key: "tier", header: "난이도", hideBelow: "sm", align: "center", render: (row) => row.tier },
  ];
  return base.map((column, index) => ({ ...column, ...overrides[index] }));
}

describe("Table (#79)", () => {
  /*
      **#379 가 난 자리다.**

      전에는 "첫 칸이 행 링크" 라는 규칙이 따로 있었다. #204 가 번호 열을 맨 앞에
      넣자 **열 순서만 바꿨는데 링크가 제목에서 번호로 옮겨 갔다** — 옮긴 사람이
      알 방법이 없었다.

      지금 규칙은 "열이 자기 목적지를 가진다" 이고, 이 시험이 그것을 붙잡는다.
      번호 열이 앞에 있어도 링크는 제목에 있어야 한다.
  */
  test("링크는 첫 칸이 아니라 href 를 선언한 열에 붙는다", () => {
    render(<Table columns={columns()} rows={ROWS} rowKey={(row) => row.id} />);

    const link = screen.getByRole("link", { name: "A+B" });
    expect(link.getAttribute("href")).toBe("/problems/1000");
    // 번호 칸은 링크가 아니다 — 맨 앞이라는 이유로 링크가 되면 #379 가 다시 난다.
    expect(screen.queryByRole("link", { name: "1000" })).toBeNull();
  });

  test("행마다 자기 목적지로 간다", () => {
    render(<Table columns={columns()} rows={ROWS} rowKey={(row) => row.id} />);

    expect(screen.getByRole("link", { name: "A+B" }).getAttribute("href")).toBe("/problems/1000");
    expect(screen.getByRole("link", { name: "A-B" }).getAttribute("href")).toBe("/problems/1001");
  });

  /** `null` 을 돌려준 행은 링크가 아니다 — 비공개 제출처럼 갈 곳이 없는 줄이 있다. */
  test("목적지가 null 인 행은 링크로 만들지 않는다", () => {
    const only1000: Column<Row>[] = columns([{}, { href: (row) => (row.id === 1000 ? "/problems/1000" : null) }]);
    render(<Table columns={only1000} rows={ROWS} rowKey={(row) => row.id} />);

    expect(screen.getByRole("link", { name: "A+B" })).toBeTruthy();
    expect(screen.queryByRole("link", { name: "A-B" })).toBeNull();
  });

  /**
   * 감추는 열은 **머리글과 값이 같이** 감춰져야 한다 (#193).
   *
   * 한쪽만 감추면 좁은 화면에서 축이 한 칸씩 밀린다 — 그것이 #193 의 증상이었다.
   */
  test("감추는 열은 머리글과 값에 같은 클래스가 붙는다", () => {
    render(<Table columns={columns()} rows={ROWS} rowKey={(row) => row.id} />);

    const header = screen.getByRole("columnheader", { name: "난이도" });
    const cell = screen.getAllByRole("cell").find((each) => each.textContent === "브론즈 5")!;

    for (const element of [header, cell]) {
      expect(element.className).toContain("hidden");
      expect(element.className).toContain("sm:table-cell");
      expect(element.className).toContain("text-center");
    }
  });

  test("머리글은 글자가 아니어도 된다", () => {
    // 전체 선택 체크박스가 머리에 서야 한다 (#627).
    const withCheckbox = columns([{ header: <input type="checkbox" aria-label="전체 선택" /> }]);
    render(<Table columns={withCheckbox} rows={ROWS} rowKey={(row) => row.id} />);

    expect(screen.getByRole("checkbox", { name: "전체 선택" })).toBeTruthy();
  });

  /** 열리는 칸은 표 폭을 다 쓴다 — 안 그러면 격자가 무너진다 (#633). */
  test("여는 칸은 열 수만큼 폭을 차지한다", () => {
    render(
      <Table
        columns={columns()}
        rows={ROWS}
        rowKey={(row) => row.id}
        expanded={(row) => (row.id === 1000 ? <p>도메인 붙이기</p> : null)}
      />,
    );

    const opened = screen.getByText("도메인 붙이기").closest("td")!;
    expect(opened.getAttribute("colspan")).toBe("3");
  });

  /** **열 것이 없으면 빈 줄도 없다.** 있으면 목록에 이유 없는 틈이 생긴다. */
  test("열 것이 없는 행은 줄을 만들지 않는다", () => {
    render(
      <Table
        columns={columns()}
        rows={ROWS}
        rowKey={(row) => row.id}
        expanded={(row) => (row.id === 1000 ? <p>열림</p> : null)}
      />,
    );

    // 머리 1 + 값 2 + 열린 칸 1 = 4
    expect(screen.getAllByRole("row")).toHaveLength(4);
  });

  test("행이 없어도 머리글은 그린다", () => {
    render(<Table columns={columns()} rows={[]} rowKey={(row: Row) => row.id} />);

    expect(screen.getAllByRole("columnheader")).toHaveLength(3);
    expect(screen.getAllByRole("row")).toHaveLength(1);
  });

  test("머리글은 열 방향임을 밝힌다", () => {
    render(<Table columns={columns()} rows={ROWS} rowKey={(row) => row.id} />);

    const head = screen.getAllByRole("rowgroup")[0];
    for (const header of within(head).getAllByRole("columnheader")) {
      expect(header.getAttribute("scope")).toBe("col");
    }
  });
});
