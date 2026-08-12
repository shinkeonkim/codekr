/**
 * 칸 하나가 어디로 가는지 정한다 (#197).
 *
 * **열이 자기 목적지를 가지면 그것이 이긴다** — 행 전체 링크보다 구체적이다.
 * 이 규칙이 없으면 목록의 모든 칸이 같은 곳으로 가고, 열 이름과 목적지가 어긋난다.
 */
export function cellTarget<T>(
  column: { href?: (row: T) => string | null },
  row: T,
  rowHref: ((row: T) => string) | undefined,
): string | null {
  if (column.href) return column.href(row);
  return rowHref?.(row) ?? null;
}
