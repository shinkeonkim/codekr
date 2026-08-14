import { parseSqlResult } from "@/entities/submission";

/** 화면에 그리는 최대 행. 실행은 시험해 보는 자리라 전부 그릴 이유가 없다. */
const MAX_ROWS = 50;

/**
 * SQL 실행 결과를 표로 (#525).
 *
 * **표를 다루는 문제인데 결과가 표가 아니었다.** 파이프로 이어 붙은 줄이 그대로
 * 보였고, 열 이름이 아예 없어서 첫 칸이 `id` 인지 `member_id` 인지 알 수 없었다.
 *
 * 표로 읽히지 않으면 `null` 을 돌려준다 — 부르는 쪽이 지금까지처럼 글자 그대로 보인다.
 * `INSERT`·`UPDATE`(#453)처럼 **결과 집합이 없는 쿼리**가 그 자리다.
 */
export function SqlResultTable({ stdout }: { stdout: string }) {
  const table = parseSqlResult(stdout);
  if (!table) return null;

  const shown = table.rows.slice(0, MAX_ROWS);

  return (
    <div>
      <p className="mb-1 text-xs font-medium text-ink-muted">
        결과 {table.rows.length}행
        {table.rows.length > MAX_ROWS ? ` (처음 ${MAX_ROWS}행만 보입니다)` : ""}
      </p>
      {/* 열이 많으면 가로로 넘긴다. 표가 페이지를 밀어내면 아래 것들이 다 어긋난다. */}
      <div className="max-h-64 overflow-auto rounded-lg border border-border">
        <table className="w-full text-left text-xs">
          <thead className="sticky top-0 bg-surface-muted">
            <tr>
              {table.columns.map((column, index) => (
                <th key={`${column}-${index}`} className="whitespace-nowrap px-3 py-2 font-medium text-ink">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {shown.length === 0 ? (
              <tr>
                <td colSpan={table.columns.length} className="px-3 py-3 text-ink-muted">
                  조건에 맞는 행이 없습니다.
                </td>
              </tr>
            ) : (
              shown.map((row, rowIndex) => (
                <tr key={rowIndex} className="border-t border-border">
                  {table.columns.map((_, columnIndex) => (
                    <td key={columnIndex} className="whitespace-pre-wrap px-3 py-1.5 align-top text-ink">
                      {/*
                        **NULL 은 빈 값과 다르게 보여야 한다.** 둘 다 빈 칸으로 두면
                        "값이 없다" 와 "빈 글자" 를 화면에서 가릴 수 없다.
                      */}
                      {row[columnIndex] === null ? (
                        <span className="text-ink-muted">NULL</span>
                      ) : (
                        row[columnIndex]
                      )}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
