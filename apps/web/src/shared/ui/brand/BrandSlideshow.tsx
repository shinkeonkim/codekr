"use client";

import { useEffect, useState } from "react";
import { BANNER_HEIGHT, BANNER_WIDTH, BrandBanner } from "./BrandBanner";
import type { BrandBannerName } from "./BrandBanner";

/** 한 장이 머무는 시간. 짧으면 읽는 도중에 바뀌고, 길면 두 번째 장을 아무도 못 본다. */
const DEFAULT_INTERVAL_MS = 6000;

/**
 * 목록 위에 까는 배너 슬라이드쇼 (#461).
 *
 * **저절로 움직이는 것에는 멈출 방법이 있어야 한다** (WCAG 2.2.2). 여기서는 셋이다 —
 * 시스템이 "움직임 줄이기" 면 아예 안 돌고, 포인터·키보드가 안에 있으면 멈추고,
 * 점을 한 번 누르면 **그 뒤로는 영영 안 돈다.** 손으로 고른 사람의 선택을 뺏지 않는다.
 *
 * 그림은 전부 장식이라 `alt` 가 비어 있다. 그래서 이 덩어리는 **아무 정보도 나르지 않는다** —
 * 점 버튼에만 이름을 달아 두고, 그림 자체는 스크린 리더가 지나치게 둔다.
 */
export function BrandSlideshow({
  names,
  intervalMs = DEFAULT_INTERVAL_MS,
}: {
  names: readonly BrandBannerName[];
  intervalMs?: number;
}) {
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  /** 손으로 고른 뒤에는 자동 전환을 되살리지 않는다. */
  const [stopped, setStopped] = useState(false);

  useEffect(() => {
    if (stopped || paused || names.length < 2) return;
    // 브라우저 설정이 움직임을 줄이라고 하면 첫 장에서 멈춘다.
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const timer = setInterval(() => {
      setCurrent((it) => (it + 1) % names.length);
    }, intervalMs);
    return () => clearInterval(timer);
  }, [names.length, intervalMs, paused, stopped]);

  if (names.length === 0) return null;

  return (
    <div
      className="space-y-2"
      onPointerEnter={() => setPaused(true)}
      onPointerLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
    >
      {/*
        높이를 비율로 먼저 잡는다. 장이 전부 겹쳐 떠 있어서 이것이 없으면 높이가 0 이 되고,
        그림이 도착하는 순간 아래 목록이 통째로 밀린다.
      */}
      <div className="relative" style={{ aspectRatio: `${BANNER_WIDTH} / ${BANNER_HEIGHT}` }}>
        {names.map((name, index) => (
          <BrandBanner
            key={name}
            name={name}
            // 첫 장만 미리 받는다. 나머지는 6초 뒤에 필요하다.
            priority={index === 0}
            className={`absolute inset-0 h-full object-cover transition-opacity duration-700 motion-reduce:transition-none ${
              index === current ? "opacity-100" : "opacity-0"
            }`}
          />
        ))}
      </div>

      {names.length > 1 ? (
        <div className="flex justify-center gap-2">
          {names.map((name, index) => (
            <button
              key={name}
              type="button"
              aria-label={`${index + 1}번째 그림 보기`}
              aria-current={index === current}
              onClick={() => {
                setCurrent(index);
                setStopped(true);
              }}
              className={`h-2 rounded-full transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/40 ${
                index === current ? "w-6 bg-brand" : "w-2 bg-border hover:bg-ink-muted"
              }`}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}
