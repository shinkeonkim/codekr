"use client";

import { cn } from "@/shared/lib";
import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import type { ComponentProps, ReactNode } from "react";

/**
 * 툴팁 (#291 4단계).
 *
 * **`title=` 을 걷는다.** 브라우저 기본 툴팁은 뜨는 데 1초 넘게 걸리고, 모양·위치를
 * 손댈 수 없고, **키보드로 닿지 않는다.** 아이콘만 있는 버튼의 뜻을 그것으로 알리면
 * 마우스를 쓰지 않는 사람에게는 뜻이 없다.
 *
 * **`aria-label` 을 대신하지 않는다.** 툴팁은 보이는 설명이고, 이름은 따로 있어야
 * 한다 — 스크린 리더는 시각적 툴팁을 읽지 않는다.
 *
 * **터치에서는 뜨지 않는다.** Radix 가 그렇게 만들었고 그것이 옳다 — 터치에는 hover 가
 * 없어서 툴팁을 띄우려면 탭을 가로채야 하고, 그러면 버튼이 눌리지 않는다. 그래서
 * **터치에서도 떠야 하는 곳은 이것을 쓰지 않는다** (활동 그래프의 칸이 그렇다, #133).
 */
export function Tooltip({
  content,
  children,
  side = "top",
  delayDuration = 200,
}: {
  content: ReactNode;
  children: ReactNode;
  side?: ComponentProps<typeof TooltipPrimitive.Content>["side"];
  /** 기본 `title` 보다 빨리 뜬다 — 기다림 자체가 그것의 문제였다. */
  delayDuration?: number;
}) {
  return (
    <TooltipPrimitive.Provider delayDuration={delayDuration}>
      <TooltipPrimitive.Root>
        <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
        <TooltipPrimitive.Portal>
          <TooltipPrimitive.Content
            side={side}
            sideOffset={6}
            className={cn(
              "z-tooltip w-max max-w-[16rem] rounded-lg border border-border bg-surface",
              "px-2.5 py-1.5 text-xs text-ink shadow-lg",
              "animate-in fade-in-0 zoom-in-95",
            )}
          >
            {content}
          </TooltipPrimitive.Content>
        </TooltipPrimitive.Portal>
      </TooltipPrimitive.Root>
    </TooltipPrimitive.Provider>
  );
}
