"use client";

import { Card } from "./ui";
import type { ActivityResponse } from "@/lib/types";

/** 활동량을 4단계로 나눈다. 색만으로 구분하지 않도록 각 칸에 정확한 수치를 함께 담는다. */
const LEVELS = [
  { min: 0, className: "bg-surface-muted", label: "없음" },
  { min: 1, className: "bg-ok/25", label: "1~2회" },
  { min: 3, className: "bg-ok/50", label: "3~5회" },
  { min: 6, className: "bg-ok/75", label: "6~9회" },
  { min: 10, className: "bg-ok", label: "10회 이상" },
];

const WEEKDAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

function levelOf(count: number): number {
  let level = 0;
  LEVELS.forEach((entry, index) => {
    if (count >= entry.min) level = index;
  });
  return level;
}

/** 날짜 문자열을 시간대 해석 없이 다루기 위해 UTC 기준으로 파싱한다. */
function parseDate(value: string): Date {
  return new Date(`${value}T00:00:00Z`);
}

function toKey(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/**
 * GitHub 잔디 형태의 활동 그래프.
 *
 * 주 단위 열로 쌓고, 각 칸은 하루다. 색은 강도를 빠르게 훑기 위한 것이고,
 * 실제 정보(날짜·활동량)는 `title` 과 스크린 리더용 텍스트로 함께 제공한다.
 */
export function ActivityGraph({ activity }: { activity: ActivityResponse }) {
  const counts = new Map(activity.days.map((day) => [day.date, day.count]));

  // 그래프가 항상 일요일에서 시작하도록 시작일을 그 주의 일요일로 당긴다.
  const start = parseDate(activity.from);
  start.setUTCDate(start.getUTCDate() - start.getUTCDay());
  const end = parseDate(activity.to);

  const weeks: Date[][] = [];
  for (let cursor = new Date(start); cursor <= end; cursor.setUTCDate(cursor.getUTCDate() + 1)) {
    const day = new Date(cursor);
    if (day.getUTCDay() === 0) weeks.push([]);
    weeks[weeks.length - 1]?.push(day);
  }

  return (
    <Card className="space-y-4 p-5">
      <div className="flex flex-wrap items-center gap-4">
        <Stat label="현재 스트릭" value={`${activity.currentStreak}일`} />
        <Stat label="최장 스트릭" value={`${activity.longestStreak}일`} />
        <Stat label="활동한 날" value={`${activity.activeDayCount}일`} />
        <Stat label="채점 완료" value={`${activity.totalCount}회`} />
      </div>

      <div className="overflow-x-auto">
        <table className="border-separate border-spacing-[3px]">
          <caption className="sr-only">
            {activity.from}부터 {activity.to}까지의 일별 활동. 기준 시간대는 {activity.timeZone}.
          </caption>
          <tbody>
            {WEEKDAY_LABELS.map((weekday, weekdayIndex) => (
              <tr key={weekday}>
                <th scope="row" className="pr-1 text-right align-middle text-[10px] text-ink-muted">
                  {/* 월·수·금만 표시해 촘촘함을 덜어낸다. */}
                  {weekdayIndex % 2 === 1 ? weekday : ""}
                </th>
                {weeks.map((week) => {
                  const day = week.find((date) => date.getUTCDay() === weekdayIndex);
                  if (!day) return <td key={`${weekday}-empty`} className="h-3 w-3" />;

                  const key = toKey(day);
                  const count = counts.get(key) ?? 0;
                  const description = `${key}: 채점 완료 ${count}회`;
                  return (
                    <td key={key} className="p-0">
                      <div
                        className={`h-3 w-3 rounded-sm ${LEVELS[levelOf(count)].className}`}
                        title={description}
                      >
                        <span className="sr-only">{description}</span>
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center gap-2 text-[11px] text-ink-muted">
        <span>적음</span>
        {LEVELS.map((level) => (
          <span key={level.label} className="flex items-center gap-1">
            <span className={`inline-block h-3 w-3 rounded-sm ${level.className}`} aria-hidden />
            <span className="sr-only">{level.label}</span>
          </span>
        ))}
        <span>많음</span>
        <span className="ml-2">({LEVELS.map((level) => level.label).join(" · ")})</span>
      </div>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-ink-muted">{label}</p>
      <p className="mt-0.5 text-lg font-semibold text-ink">{value}</p>
    </div>
  );
}
