import type { ReactNode } from "react";

export interface Column<T> {
  key: string;
  header: string;
  /** 좁은 화면에서 숨긴다. 목록에서 판단에 꼭 필요하지 않은 열에 쓴다. */
  hideOnMobile?: boolean;
  align?: "left" | "right";
  render: (row: T) => ReactNode;
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  /** 행 전체를 링크로 만들 때의 목적지. */
  href?: (row: T) => string;
}

/**
 * 목록용 테이블 (#79).
 *
 * 목록의 목적은 훑어보고 고르는 것이다. 카드보다 조밀하고 열이 정렬돼 있어야 비교가 된다.
 *
 * 좁은 화면 대응은 **열을 감추는 방식**으로 한다. 가로 스크롤은 존재를 눈치채기 어렵고,
 * 카드로 전환하면 테이블을 쓴 이유(조밀함)가 사라진다.
 */
export function Table<T>({ columns, rows, rowKey, href }: Props<T>) {
  return (
    <div className="overflow-hidden rounded-card border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border bg-surface-muted/50 text-left">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={`px-4 py-2.5 text-xs font-medium text-ink-muted ${
                  column.align === "right" ? "text-right" : ""
                } ${column.hideOnMobile ? "hidden sm:table-cell" : ""}`}
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
              {columns.map((column, index) => (
                <td
                  key={column.key}
                  className={`px-4 py-3 ${column.align === "right" ? "text-right" : ""} ${
                    column.hideOnMobile ? "hidden sm:table-cell" : ""
                  }`}
                >
                  {/* 행 전체를 <a> 로 감싸면 HTML 이 깨지므로 첫 칸만 링크로 만들고 나머지는 그대로 둔다. */}
                  {href && index === 0 ? (
                    <a href={href(row)} className="block font-medium text-ink hover:text-brand">
                      {column.render(row)}
                    </a>
                  ) : (
                    column.render(row)
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
