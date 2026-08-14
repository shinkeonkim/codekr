import type { SeriesPoint } from "./ticks";

/**
 * 비중 (판정·언어·유형) (#550).
 *
 * **원 그래프를 쓰지 않는다.** 사람은 각도를 잘 못 읽고, 항목이 여덟을 넘으면 색을
 * 구분하는 것도 어렵다. 가로 막대는 순서와 길이로 읽히고 이름을 그대로 붙일 수 있다.
 *
 * 눈금선과 툴팁을 더하지 않은 이유(#579): **값과 비율이 이미 글자로 옆에 있다.**
 * 여기서 툴팁은 이미 보이는 것을 한 번 더 보여 주는 일이 된다.
 */
export function BarChart({ items, label }: { items: SeriesPoint[]; label: string }) {
  if (items.length === 0) {
    return <p className="py-6 text-center text-xs text-ink-muted">아직 자료가 없습니다.</p>;
  }

  const max = Math.max(...items.map((item) => item.value), 1);
  const total = items.reduce((sum, item) => sum + item.value, 0);

  return (
    <ul className="space-y-1.5" aria-label={label}>
      {items.map((item) => (
        <li key={item.label} className="grid grid-cols-[8rem_1fr_4.5rem] items-center gap-2 text-xs">
          <span className="truncate text-ink" title={item.label}>
            {item.label}
          </span>
          <span className="h-2 rounded-full bg-surface-muted">
            <span
              className="block h-2 rounded-full bg-brand"
              style={{ width: `${(item.value / max) * 100}%` }}
            />
          </span>
          <span className="text-right text-ink-muted">
            {item.value.toLocaleString("ko-KR")}
            {total > 0 ? ` · ${Math.round((item.value / total) * 100)}%` : ""}
          </span>
        </li>
      ))}
    </ul>
  );
}
