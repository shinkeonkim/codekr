import type { ReactNode } from "react";
import type { Align, Breakpoint } from "./tableCell";
import { cellClass } from "./tableCell";
import { cellTarget } from "./tableLink";

export interface Column<T> {
  key: string;
  header: string;
  /**
   * 이 폭보다 좁으면 감춘다 (#193).
   *
   * **좁은 화면에 남는 열은 셋까지다.** 넷째부터는 한 줄이 접혀서 오히려 못 읽는다.
   * 그래서 고르는 데 꼭 필요한 열만 남기고, 판단을 돕는 열은 `sm`, 참고용 수치는 `lg`.
   */
  hideBelow?: Breakpoint;
  /**
   * 값이 균일한 수치 열은 `center` 로 둔다 (#193) — 세로로 비교되려면 축이 맞아야 한다.
   * 길이가 제각각인 텍스트는 왼쪽 그대로다. 가운데로 몰면 줄마다 시작점이 흔들린다.
   */
  align?: Align;
  /**
   * 이 열만의 목적지 (#197).
   *
   * **행 하나에 목적지가 하나뿐이면 열 이름과 가는 곳이 어긋난다.** 제출 목록에서
   * "문제" 열을 눌렀는데 제출 상세로 가던 것이 그 예다.
   *
   * `null` 을 돌려주면 그 행에서는 링크로 만들지 않는다.
   */
  href?: (row: T) => string | null;
  /**
   * 이 열의 폭을 고정하는 클래스 (`w-20` 등).
   *
   * **자릿수가 늘 때 옆 열이 밀리는 것을 막는다** (#288). 제출 번호처럼 값의 길이가
   * 자라는 열에만 쓴다 — 글자 열에 폭을 박으면 잘린다.
   */
  width?: string;
  render: (row: T) => ReactNode;
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
}

/**
 * 목록용 테이블 (#79).
 *
 * 목록의 목적은 훑어보고 고르는 것이다. 카드보다 조밀하고 열이 정렬돼 있어야 비교가 된다.
 *
 * 좁은 화면 대응은 **열을 감추는 방식**으로 한다. 가로 스크롤은 존재를 눈치채기 어렵고,
 * 카드로 전환하면 테이블을 쓴 이유(조밀함)가 사라진다.
 */
export function Table<T>({ columns, rows, rowKey }: Props<T>) {
  return (
    <div className="overflow-hidden rounded-card border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border bg-surface-muted/50 text-left">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={`px-4 py-2.5 text-xs font-medium text-ink-muted ${cellClass(column)}`}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              className="border-b border-border/60 transition last:border-0 hover:bg-surface-muted/40"
            >
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={`px-4 py-3 ${cellClass(column)}`}
                >
                  {/*
                    행 전체를 `<a>` 로 감싸면 HTML 이 깨지므로 칸 단위로 링크를 만든다.

                    **어느 칸이 링크인지는 열이 스스로 말한다** (`Column.href`, #379).
                    전에는 "첫 칸" 이라는 규칙이 따로 있었는데, #204 가 번호 열을 맨 앞에
                    넣으면서 **링크가 제목에서 번호로 조용히 옮겨 갔다.** 열 순서를 바꾸는
                    사람이 링크가 함께 움직인다는 것을 알 방법이 없었다.
                  */}
                  <CellContent column={column} row={row} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CellContent<T>({ column, row }: { column: Column<T>; row: T }) {
  const target = cellTarget(column, row);
  if (!target) return <>{column.render(row)}</>;

  return (
    <a href={target} className="block font-medium text-ink hover:text-brand">
      {column.render(row)}
    </a>
  );
}
