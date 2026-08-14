"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { BrandBanner, bannerRatio } from "./BrandBanner";
import type { BrandBannerName } from "./BrandBanner";
import { buildTrack, dotIndex, settleTarget } from "./slideTrack";

/** 한 장이 머무는 시간. 짧으면 읽는 도중에 바뀌고, 길면 두 번째 장을 아무도 못 본다. */
const DEFAULT_INTERVAL_MS = 10000;

/** 한 장이 옆으로 밀리는 데 걸리는 시간. 아래 `duration-700` 과 같아야 한다. */
const SLIDE_MS = 700;

/**
 * 목록 위에 까는 배너 슬라이드쇼 (#461, #518, #523).
 *
 * **저절로 움직이는 것에는 멈출 방법이 있어야 한다** (WCAG 2.2.2). 여기서는 셋이다 —
 * 시스템이 "움직임 줄이기" 면 아예 안 돌고, 포인터·키보드가 안에 있으면 멈추고,
 * 손으로 한 번 넘기면 **그 뒤로는 영영 안 돈다.** 고른 사람의 선택을 뺏지 않는다.
 *
 * **되감기지 않는다** (#523). 장을 겹쳐 놓고 투명도만 바꾸면 방향이 없고, 마지막에서
 * 첫 장으로 갈 때 순서가 3 → 1 로 뛴다. 그래서 옆으로 미는 트랙으로 놓고 **양 끝에
 * 사본을 한 장씩 덧댄다** — `[3'] [1] [2] [3] [1']`. 마지막 다음에는 계속 앞으로 밀어
 * 끝의 사본으로 가고, 다 민 뒤 **애니메이션을 끄고** 진짜 자리로 옮긴다.
 *
 * 그림은 전부 장식이라 `alt` 가 비어 있다. 그래서 이 덩어리는 **아무 정보도 나르지 않는다** —
 * 단추에만 이름을 달아 두고, 그림 자체는 스크린 리더가 지나치게 둔다.
 */
export function BrandSlideshow({
  names,
  intervalMs = DEFAULT_INTERVAL_MS,
}: {
  names: readonly BrandBannerName[];
  intervalMs?: number;
}) {
  const loop = names.length > 1;
  /** 트랙 위의 자리. 사본이 앞에 하나 있으므로 **1 이 첫 장**이다. */
  const [position, setPosition] = useState(loop ? 1 : 0);
  const [sliding, setSliding] = useState(true);
  const [paused, setPaused] = useState(false);
  /** 손으로 넘긴 뒤에는 자동 전환을 되살리지 않는다. */
  const [stopped, setStopped] = useState(false);

  const move = useCallback(
    (delta: number) => setPosition((it) => it + delta),
    [],
  );

  useEffect(() => {
    if (stopped || paused || !loop) return;
    // 브라우저 설정이 움직임을 줄이라고 하면 첫 장에서 멈춘다.
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const timer = setInterval(() => move(1), intervalMs);
    return () => clearInterval(timer);
  }, [loop, intervalMs, paused, stopped, move]);

  /**
   * 사본 자리에 도착했으면 진짜 자리로 **소리 없이** 옮긴다.
   *
   * **`transitionend` 에 기대지 않는다.** 탭이 뒤에 있으면 브라우저가 그리기를 멈춰
   * 그 사건이 아예 오지 않는데, `setInterval` 은 계속 돌아서 자리만 끝없이 밀린다 —
   * 돌아와 보면 빈 곳이 보인다. 그래서 **시간으로** 잡는다.
   */
  useEffect(() => {
    const target = settleTarget(position, names.length);
    if (target === null) return;

    const timer = setTimeout(() => {
      setSliding(false);
      setPosition(target);
    }, SLIDE_MS);
    return () => clearTimeout(timer);
  }, [position, names.length]);

  const framesRef = useRef<number[]>([]);
  useEffect(() => {
    if (sliding) return;
    const frames = framesRef.current;
    frames.push(
      requestAnimationFrame(() => {
        frames.push(requestAnimationFrame(() => setSliding(true)));
      }),
    );
    return () => {
      frames.splice(0).forEach(cancelAnimationFrame);
    };
  }, [sliding]);

  if (names.length === 0) return null;

  const track = buildTrack(names);
  const current = dotIndex(position, names.length);

  const step = (delta: number) => {
    setStopped(true);
    move(delta);
  };

  return (
    <div
      className="space-y-2"
      onPointerEnter={() => setPaused(true)}
      onPointerLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
    >
      {/*
        높이를 비율로 먼저 잡는다. 이것이 없으면 높이가 0 이 되고, 그림이 도착하는
        순간 아래 목록이 통째로 밀린다.

        **첫 장의 비율로 잡는다** (#518). 비율이 다른 그림은 `object-cover` 가 잘라 낸다.
      */}
      <div
        className="group relative overflow-hidden"
        style={{ aspectRatio: bannerRatio(names[0]) }}
      >
        <div
          className={`flex h-full ${sliding ? "transition-transform duration-700 ease-out" : ""} motion-reduce:transition-none`}
          style={{ transform: `translateX(-${position * 100}%)` }}
        >
          {track.map((name, index) => (
            <BrandBanner
              key={`${name}-${index}`}
              name={name}
              // 첫 장만 미리 받는다. 나머지는 10초 뒤에 필요하다.
              priority={index === (loop ? 1 : 0)}
              className="h-full w-full shrink-0 object-cover"
            />
          ))}
        </div>

        {loop ? (
          <>
            <SlideArrow side="left" label="이전 그림" onClick={() => step(-1)} />
            <SlideArrow side="right" label="다음 그림" onClick={() => step(1)} />
          </>
        ) : null}
      </div>

      {loop ? (
        <div className="flex justify-center gap-2">
          {names.map((name, index) => (
            <button
              key={name}
              type="button"
              aria-label={`${index + 1}번째 그림 보기`}
              aria-current={index === current}
              onClick={() => {
                setStopped(true);
                setPosition(index + 1);
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

/**
 * 그림 위에 얹는 이전·다음 단추.
 *
 * **늘 보인다.** hover 일 때만 나타나게 하면 손가락으로 쓰는 기기에서는 있는 줄도 모른다 —
 * 터치에는 "다가감" 이 없다. 대신 반투명한 바탕을 깔아 그림을 덜 가린다.
 */
function SlideArrow({
  side,
  label,
  onClick,
}: {
  side: "left" | "right";
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={`absolute top-1/2 -translate-y-1/2 ${side === "left" ? "left-2" : "right-2"} grid h-9 w-9 place-items-center rounded-full bg-surface/80 text-ink shadow-sm backdrop-blur transition hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/40`}
    >
      <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d={side === "left" ? "M15 5l-7 7 7 7" : "M9 5l7 7-7 7"}
        />
      </svg>
    </button>
  );
}
