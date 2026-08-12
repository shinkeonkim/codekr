"use client";

import { cn } from "@/shared/lib";
import * as SelectPrimitive from "@radix-ui/react-select";
import { CheckIcon, ChevronDownIcon, ChevronUpIcon } from "lucide-react";
import type { ComponentProps } from "react";

/**
 * 선택기 (#287). shadcn/ui 의 Select 를 이 저장소 토큰에 맞춰 들여왔다.
 *
 * **바꾼 이유는 열린 목록이다.** 네이티브 `<select>` 는 테두리만 우리 것이고 안은
 * 운영체제 것이라, #206 으로 테마를 골라도 **드롭다운을 열면 그 선택이 무시됐다.**
 *
 * 색은 `bg-popover`·`text-foreground` 처럼 shadcn 이름으로 적는다 — `globals.css`
 * 에서 우리 토큰의 별명으로 이어 두었다. 위쪽 코드를 upstream 과 가깝게 두어야
 * 다음에 shadcn 이 고쳐질 때 그대로 가져올 수 있다.
 */

export const Select = SelectPrimitive.Root;
export const SelectGroup = SelectPrimitive.Group;
export const SelectValue = SelectPrimitive.Value;

export function SelectTrigger({
  className,
  children,
  ...props
}: ComponentProps<typeof SelectPrimitive.Trigger>) {
  return (
    <SelectPrimitive.Trigger
      className={cn(
        "flex w-full items-center justify-between gap-2 rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground outline-none",
        "focus:border-primary disabled:cursor-not-allowed disabled:opacity-50",
        "data-[placeholder]:text-muted-foreground [&>span]:truncate",
        className,
      )}
      {...props}
    >
      {children}
      <SelectPrimitive.Icon asChild>
        <ChevronDownIcon className="size-4 shrink-0 text-muted-foreground" />
      </SelectPrimitive.Icon>
    </SelectPrimitive.Trigger>
  );
}

export function SelectContent({
  className,
  children,
  position = "popper",
  // **트리거에서 띄운다** (#306). 붙어 있으면 항목을 고른 탭이 목록이 닫힌 자리
  // 아래의 트리거로 뚫고 내려가 다시 열린다 — 터치에서 흔한 증상이다.
  sideOffset = 4,
  ...props
}: ComponentProps<typeof SelectPrimitive.Content>) {
  return (
    <SelectPrimitive.Portal>
      <SelectPrimitive.Content
        // `z-tooltip` 인 이유: 헤더(`z-header`)보다 위여야 한다. 헤더 안의 선택기가
        // 열렸을 때 목록이 헤더 뒤로 숨으면 고를 수가 없다 (#134 의 층 표).
        className={cn(
          "relative z-tooltip max-h-96 min-w-32 overflow-y-auto overflow-x-hidden rounded-lg border border-border bg-popover text-popover-foreground shadow-lg",
          position === "popper" && [
            "w-[var(--radix-select-trigger-width)]",
            "data-[side=bottom]:translate-y-1 data-[side=top]:-translate-y-1",
            "data-[side=left]:-translate-x-1 data-[side=right]:translate-x-1",
          ],
          className,
        )}
        position={position}
        sideOffset={sideOffset}
        {...props}
      >
        <SelectPrimitive.ScrollUpButton className="flex items-center justify-center py-1">
          <ChevronUpIcon className="size-4 text-muted-foreground" />
        </SelectPrimitive.ScrollUpButton>
        <SelectPrimitive.Viewport className="p-1">{children}</SelectPrimitive.Viewport>
        <SelectPrimitive.ScrollDownButton className="flex items-center justify-center py-1">
          <ChevronDownIcon className="size-4 text-muted-foreground" />
        </SelectPrimitive.ScrollDownButton>
      </SelectPrimitive.Content>
    </SelectPrimitive.Portal>
  );
}

export function SelectItem({
  className,
  children,
  ...props
}: ComponentProps<typeof SelectPrimitive.Item>) {
  return (
    <SelectPrimitive.Item
      className={cn(
        "relative flex w-full cursor-default select-none items-center gap-2 rounded-md py-1.5 pl-2 pr-8 text-sm outline-none",
        "focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
        className,
      )}
      {...props}
    >
      <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
      <span className="absolute right-2 flex size-4 items-center justify-center">
        <SelectPrimitive.ItemIndicator>
          <CheckIcon className="size-4 text-primary" />
        </SelectPrimitive.ItemIndicator>
      </span>
    </SelectPrimitive.Item>
  );
}
