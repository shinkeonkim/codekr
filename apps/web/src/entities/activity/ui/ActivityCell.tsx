"use client";

import { useState } from "react";

/**
 * 칸 한 변의 크기. **한 곳에서만 정한다** (#196).
 *
 * 감싸는 상자·버튼·빈 칸이 각자 크기를 들고 있으면 하나만 바뀌어도 격자가 어긋난다.
 */
export const CELL_SIZE = "h-3 w-3";

/**
 * 활동 그래프의 칸 하나와 툴팁 (#133).
 *
 * 브라우저 기본 `title` 을 쓰지 않는 이유:
 *   1. 뜨는 데 1초 넘게 걸리고 모양·위치를 손댈 수 없다
 *   2. **터치 기기에서는 아예 뜨지 않는다**
 *   3. 키보드로는 닿지 않는다
 *
 * `sr-only` 텍스트는 그대로 둔다. **툴팁이 그것을 대체하지 않는다** —
 * 스크린 리더는 시각적 툴팁을 읽지 않는다.
 */
export function ActivityCell({
  label,
  levelClassName,
  submissions,
  solved,
  /** 그래프 왼쪽·오른쪽 끝이면 툴팁이 화면 밖으로 나가지 않게 붙이는 쪽을 바꾼다. */
  align = "center",
}: {
  label: string;
  levelClassName: string;
  submissions: number;
  solved: number;
  align?: "start" | "center" | "end";
}) {
  const [open, setOpen] = useState(false);
  const description = describe(label, submissions, solved);

  return (
    /*
      **블록 상자다.** `inline-flex` 였을 때 격자의 세로 간격이 벌어졌다 (#196) —
      인라인 레벨 상자는 줄 기준선 위에 앉아서 `line-height` 만큼 아래에 여유가 생기고,
      3px 격자에서는 그것이 눈에 띈다.

      툴팁을 `absolute` 로 띄우려면 기준 상자가 필요한데, 그 상자가 레이아웃에
      끼어들지 않아야 한다.
    */
    <span className={`relative block ${CELL_SIZE}`}>
      {/*
        button 인 이유: 키보드 포커스를 받아야 하고, 포커스에서도 툴팁이 떠야 한다.
        div + tabIndex 로도 되지만 그때는 역할을 따로 알려야 한다.
      */}
      <button
        type="button"
        className={`block rounded-sm ${CELL_SIZE} ${levelClassName}`}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        // 터치에서도 뜬다. 기본 title 이 못 하던 것이다.
        onClick={() => setOpen((it) => !it)}
      >
        <span className="sr-only">{description}</span>
      </button>

      {open ? (
        <span
          role="tooltip"
          className={`pointer-events-none absolute bottom-5 z-tooltip w-max max-w-[14rem] rounded-lg border border-border bg-surface px-2.5 py-1.5 text-xs text-ink shadow-lg ${ALIGN[align]}`}
        >
          {description}
        </span>
      ) : null}
    </span>
  );
}

const ALIGN = {
  start: "left-0",
  center: "left-1/2 -translate-x-1/2",
  end: "right-0",
} as const;

function describe(label: string, submissions: number, solved: number): string {
  // 활동이 없는 날에 "0회 제출, 0문제" 라고 쓰면 읽는 사람이 숫자를 두 번 확인하게 된다.
  if (submissions === 0) return `${label} · 활동 없음`;
  // "새로 푼" 이 아니라 "맞힌" 이다 — 어제 푼 문제를 오늘 다시 맞혀도 세어진다 (#133).
  return `${label} · 제출 ${submissions}회 · 맞힌 문제 ${solved}개`;
}
