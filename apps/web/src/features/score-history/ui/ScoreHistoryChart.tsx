"use client";

import { request } from "@/shared/api";
import { Card, CardTitle } from "@/shared/ui";
import { useEffect, useState } from "react";

interface ScorePoint {
  date: string;
  score: number;
  tierLevel: number | null;
}

const WIDTH = 640;
const HEIGHT = 160;

/**
 * 점수가 어떻게 변해 왔는가 (#476).
 *
 * **활동 그래프(#117) 옆이다.** 그쪽은 "얼마나 자주 했는가" 이고 이것은 "얼마나
 * 늘었는가" 다 — 배우는 사람에게는 오르는 것이 보이는 것이 계속하는 이유가 된다.
 *
 * **"레이팅" 이라고 부르지 않는다.** 그 말은 대개 경쟁 결과로 오르내리는 값(Elo)을
 * 뜻하는데, 우리 점수는 푼 문제가 쌓이는 값이라 **내려가지 않는다** — 이름이 기대를
 * 만든다.
 *
 * 그림은 직접 그린다. 차트 라이브러리를 들이면 이 한 장을 위해 번들이 커진다.
 */
export function ScoreHistoryChart({ handle }: { handle: string }) {
  const [points, setPoints] = useState<ScorePoint[] | null>(null);

  useEffect(() => {
    let alive = true;
    request<ScorePoint[]>(
      `/api/v1/users/${encodeURIComponent(handle)}/score-history`,
    )
      .then((next) => alive && setPoints(next))
      .catch(() => alive && setPoints([]));
    return () => {
      alive = false;
    };
  }, [handle]);

  // 점이 하나도 없으면 자리를 그리지 않는다 (#391 이 열려는 0점 사용자) —
  // 빈 그래프는 "아직 안 푼 사람" 이 아니라 "고장 난 화면" 으로 보인다.
  if (!points || points.length === 0) return null;

  const max = Math.max(...points.map((point) => point.score), 1);
  const step = points.length === 1 ? 0 : WIDTH / (points.length - 1);
  const line = points
    .map(
      (point, index) =>
        `${index * step},${HEIGHT - (point.score / max) * HEIGHT}`,
    )
    .join(" ");
  const last = points[points.length - 1];
  // 티어가 바뀐 날이 이정표다 — 점수는 연속이라 변화가 보이고 티어는 계단이라 읽기 쉽다.
  const milestones = points.filter(
    (point, index) =>
      index > 0 && point.tierLevel !== points[index - 1].tierLevel,
  );

  return (
    <Card className="space-y-2 p-5">
      <CardTitle>점수 변화</CardTitle>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="h-40 w-full"
        role="img"
        aria-label={`점수 변화 그래프. 지금 ${last.score}점.`}
      >
        <polyline
          points={line}
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          className="text-brand"
        />
        {milestones.map((point) => {
          const index = points.indexOf(point);
          return (
            <circle
              key={point.date}
              cx={index * step}
              cy={HEIGHT - (point.score / max) * HEIGHT}
              r={4}
              className="fill-brand"
            >
              <title>{`${point.date} — 티어 ${point.tierLevel}`}</title>
            </circle>
          );
        })}
      </svg>
      <p className="text-xs text-ink-muted">
        {points[0].date}부터 지금까지 {last.score}점. 점은 티어가 바뀐 날입니다.
      </p>
    </Card>
  );
}
