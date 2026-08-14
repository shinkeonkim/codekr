"use client";

import { useRef, useState } from "react";
import { type SeriesPoint, axis, labelIndices, nearestIndex } from "./ticks";

/**
 * 시간에 따른 값 하나 (제출 수·가입 수) (#550, #579).
 *
 * **차트 라이브러리를 들이지 않는다.** #476 이 같은 판단을 했다 — 어드민 화면은 사용자
 * 화면과 번들을 나눠 갖지 않아서, 여기서 들인 라이브러리가 문제를 푸는 사람의 첫 화면까지
 * 무겁게 한다.
 *
 * 처음에는 `<polyline>` 하나뿐이었다. 그러면 **모양만 보이고 값을 못 읽는다** — 언제
 * 튀었는지 세로면 눈금이 없어 몇 건인지 알 수 없고, 가로면 이름표가 양 끝 둘뿐이라
 * 날짜를 셀 수 없다. "갑자기 줄면 무엇이 막힌 것" 을 보려고 만든 그림인데 정작 언제
 * 줄었는지를 DB 에 다시 물어야 했다.
 *
 * **비율을 왜곡하지 않는다.** `preserveAspectRatio="none"` 으로 세로만 늘이면 기울기가
 * 실제보다 가팔라 보인다 — 추세를 읽는 그림에서 그것은 틀린 값을 보여 주는 것과 같다.
 *
 * 그림은 **읽는 것**이므로 값이 글자로도 있어야 한다 — 스크린 리더는 `<polyline>` 을
 * 읽지 못한다. 손짓으로만 닿는 툴팁도 마찬가지라, 요약 문장을 그대로 둔다.
 */

const WIDTH = 640;
const HEIGHT = 180;
/** 이름표가 들어갈 자리. 왼쪽이 넓은 것은 세로 눈금 숫자가 거기 서기 때문이다. */
const PAD = { left: 40, right: 10, top: 12, bottom: 22 };
/** 가로 이름표의 최대 개수. 더 적으면 겹친다 (30일 치면 다섯 날마다 하나). */
const MAX_X_LABELS = 7;

const PLOT_LEFT = PAD.left;
const PLOT_RIGHT = WIDTH - PAD.right;
const PLOT_TOP = PAD.top;
const PLOT_BOTTOM = HEIGHT - PAD.bottom;

export function LineChart({
  points,
  label,
  tone = "text-brand",
}: {
  points: SeriesPoint[];
  label: string;
  tone?: string;
}) {
  const frame = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState<number | null>(null);

  if (points.length === 0) return null;

  const max = Math.max(...points.map((point) => point.value));
  const { top, values: ticks } = axis(max);
  const total = points.reduce((sum, point) => sum + point.value, 0);
  const labelled = new Set(labelIndices(points.length, MAX_X_LABELS));

  const step = points.length === 1 ? 0 : (PLOT_RIGHT - PLOT_LEFT) / (points.length - 1);
  // 점이 하나면 가운데에 둔다. 왼쪽 끝에 붙이면 "왼쪽이 잘렸나" 로 읽힌다.
  const x = (index: number) => (points.length === 1 ? (PLOT_LEFT + PLOT_RIGHT) / 2 : PLOT_LEFT + index * step);
  const y = (value: number) => PLOT_BOTTOM - (value / top) * (PLOT_BOTTOM - PLOT_TOP);
  const line = points.map((point, index) => `${x(index)},${y(point.value)}`).join(" ");

  const track = (event: React.PointerEvent<HTMLDivElement>) => {
    const box = frame.current?.getBoundingClientRect();
    if (!box) return;
    // 그림 좌표로 옮긴 뒤 자리를 고른다 — 화면 폭이 얼마든 같은 계산이 된다.
    const inView = ((event.clientX - box.left) / box.width) * WIDTH;
    setActive(nearestIndex((inView - PLOT_LEFT) / (PLOT_RIGHT - PLOT_LEFT), points.length));
  };

  const shown = active === null ? null : points[active];

  return (
    <div className="space-y-1">
      <div
        ref={frame}
        className="relative"
        onPointerMove={track}
        onPointerLeave={() => setActive(null)}
      >
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          className="w-full"
          role="img"
          aria-label={`${label}. 기간 합계 ${total}, 최댓값 ${max}.`}
        >
          {/* 세로 눈금: 값이 얼마인지. 선만 있으면 어디가 100인지 여전히 모른다. */}
          {ticks.map((tick) => (
            <g key={tick}>
              <line
                x1={PLOT_LEFT}
                x2={PLOT_RIGHT}
                y1={y(tick)}
                y2={y(tick)}
                className="stroke-border"
                strokeWidth={1}
              />
              <text
                x={PLOT_LEFT - 6}
                y={y(tick) + 3}
                textAnchor="end"
                className="fill-ink-muted text-[10px]"
              >
                {tick.toLocaleString("ko-KR")}
              </text>
            </g>
          ))}

          {/* 가로 눈금: 언제인지. 겹치느니 비운다. */}
          {points.map((point, index) =>
            labelled.has(index) ? (
              <g key={point.label}>
                <line
                  x1={x(index)}
                  x2={x(index)}
                  y1={PLOT_TOP}
                  y2={PLOT_BOTTOM}
                  className="stroke-border"
                  strokeWidth={1}
                  strokeDasharray="2 3"
                />
                <text
                  x={x(index)}
                  y={HEIGHT - 6}
                  /*
                    양 끝은 가운데 정렬을 하면 **글자 절반이 그림 밖으로 나가 잘린다.**
                    SVG 는 넘친 것을 그냥 자르므로, 마지막 날(오른쪽 끝)이 통째로
                    사라져 있었다 — 그림이 언제까지인지가 안 보였다.
                  */
                  textAnchor={index === 0 ? "start" : index === points.length - 1 ? "end" : "middle"}
                  className="fill-ink-muted text-[10px]"
                >
                  {point.label}
                </text>
              </g>
            ) : null,
          )}

          <polyline points={line} fill="none" stroke="currentColor" strokeWidth={2} className={tone} />

          {shown ? (
            <g className={tone}>
              {/* 가리킨 자리를 세로선으로 못박는다 — 툴팁 글자만 있으면 어느 점인지 흐리다. */}
              <line
                x1={x(active!)}
                x2={x(active!)}
                y1={PLOT_TOP}
                y2={PLOT_BOTTOM}
                stroke="currentColor"
                strokeWidth={1}
              />
              <circle cx={x(active!)} cy={y(shown.value)} r={3.5} fill="currentColor" />
            </g>
          ) : null}
        </svg>

        {shown ? (
          <div
            className="pointer-events-none absolute -translate-x-1/2 -translate-y-full rounded-lg border border-border bg-surface px-2 py-1 text-xs text-ink shadow-lg"
            style={{
              // 양 끝에서 화면 밖으로 나가지 않게 가둔다.
              left: `${Math.min(92, Math.max(8, (x(active!) / WIDTH) * 100))}%`,
              top: `${(y(shown.value) / HEIGHT) * 100}%`,
            }}
          >
            <span className="text-ink-muted">{shown.label}</span>{" "}
            <strong>{shown.value.toLocaleString("ko-KR")}</strong>
          </div>
        ) : null}
      </div>

      <p className="text-center text-xs text-ink-muted">
        합계 {total.toLocaleString("ko-KR")} · 최대 {max.toLocaleString("ko-KR")}
      </p>
    </div>
  );
}
