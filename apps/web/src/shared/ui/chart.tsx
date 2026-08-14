/**
 * 대시보드가 쓰는 그림 둘 (#550).
 *
 * **차트 라이브러리를 들이지 않는다.** #476 의 점수 그래프가 같은 판단을 했다 — 어드민
 * 화면은 사용자 화면과 번들을 나눠 갖지 않아서, 여기서 들인 라이브러리가 문제를 푸는
 * 사람의 첫 화면까지 무겁게 한다. 우리가 그리는 것은 선 하나와 막대 몇 개다.
 *
 * 그림은 **읽는 것**이므로 값이 글자로도 있어야 한다 — 스크린 리더는 `<polyline>` 을
 * 읽지 못한다. 그래서 둘 다 `role="img"` 와 요약 문장을 함께 낸다.
 */

const LINE_WIDTH = 640;
const LINE_HEIGHT = 140;

export interface SeriesPoint {
  label: string;
  value: number;
}

/**
 * 시간에 따른 값 하나 (제출 수·가입 수).
 *
 * **0 인 구간이 바닥에 붙어 보여야 한다.** 최댓값으로만 정규화하면 값이 다 작은 날에도
 * 그래프가 꽉 차서, 조용한 주와 바쁜 주가 같아 보인다.
 */
export function LineChart({
  points,
  label,
  tone = "text-brand",
}: {
  points: SeriesPoint[];
  label: string;
  tone?: string;
}) {
  if (points.length === 0) return null;

  const max = Math.max(...points.map((point) => point.value), 1);
  const step = points.length === 1 ? 0 : LINE_WIDTH / (points.length - 1);
  const line = points
    .map((point, index) => `${index * step},${LINE_HEIGHT - (point.value / max) * LINE_HEIGHT}`)
    .join(" ");
  const total = points.reduce((sum, point) => sum + point.value, 0);

  return (
    <div className="space-y-1">
      <svg
        viewBox={`0 0 ${LINE_WIDTH} ${LINE_HEIGHT}`}
        className="h-36 w-full"
        preserveAspectRatio="none"
        role="img"
        aria-label={`${label}. 기간 합계 ${total}, 최댓값 ${max}.`}
      >
        <polyline points={line} fill="none" stroke="currentColor" strokeWidth={2} className={tone} />
      </svg>
      <div className="flex justify-between text-xs text-ink-muted">
        <span>{points[0].label}</span>
        <span>
          합계 {total.toLocaleString("ko-KR")} · 최대 {max.toLocaleString("ko-KR")}
        </span>
        <span>{points[points.length - 1].label}</span>
      </div>
    </div>
  );
}

/**
 * 비중 (판정·언어·유형).
 *
 * **원 그래프를 쓰지 않는다.** 사람은 각도를 잘 못 읽고, 항목이 여덟을 넘으면 색을
 * 구분하는 것도 어렵다. 가로 막대는 순서와 길이로 읽히고 이름을 그대로 붙일 수 있다.
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
