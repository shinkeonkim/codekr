"use client";

import { cn } from "@/shared/lib";
import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu";
import type { ComponentProps } from "react";

/**
 * 드롭다운 메뉴 (#291 4단계).
 *
 * **지금까지 없어서 못 하던 것이다.** 헤더의 사용자 영역이 닉네임·설정·로그아웃을
 * 나란히 늘어놓고 있었는데, 항목이 늘 때마다 헤더가 길어지고 좁은 화면에서는 통째로
 * 숨겨야 했다.
 *
 * 손으로 만들지 않는 이유는 **키보드와 포커스**다. 바깥 클릭으로 닫기, Esc, 위아래
 * 이동, 열 때 첫 항목으로 포커스 이동, 닫을 때 원래 자리로 되돌리기 — 이것들을 직접
 * 만들면 대개 절반만 맞고, 그 절반이 키보드로만 쓰는 사람에게 전부다.
 */
export const DropdownMenu = DropdownMenuPrimitive.Root;
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;

export function DropdownMenuContent({
  className,
  sideOffset = 6,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Content>) {
  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        data-slot="dropdown-menu-content"
        sideOffset={sideOffset}
        className={cn(
          // `z-tooltip` 인 이유는 `Select` 와 같다 — 자기를 연 것(헤더) 위에는 떠야 하고,
          // 드로어(`z-drawer`) 아래여야 한다 (#578). 전에는 숫자를 직접 적어서 층
          // 표(#134) 밖에 있었다 — 표를 고쳐도 이 한 줄만 따라오지 않는다.
          "z-tooltip min-w-40 overflow-hidden rounded-card border border-border bg-surface p-1 shadow-lg",
          "data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0",
          "data-[state=open]:fade-in-0 data-[side=bottom]:slide-in-from-top-1",
          className,
        )}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  );
}

export function DropdownMenuItem({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Item>) {
  return (
    <DropdownMenuPrimitive.Item
      data-slot="dropdown-menu-item"
      className={cn(
        "flex cursor-pointer select-none items-center gap-2 rounded-md px-3 py-2 text-sm text-ink outline-none transition",
        "focus:bg-surface-muted data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
        className,
      )}
      {...props}
    />
  );
}

export function DropdownMenuLabel({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Label>) {
  return (
    <DropdownMenuPrimitive.Label
      data-slot="dropdown-menu-label"
      className={cn("px-3 py-2 text-xs text-ink-muted", className)}
      {...props}
    />
  );
}

export function DropdownMenuSeparator({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Separator>) {
  return (
    <DropdownMenuPrimitive.Separator
      data-slot="dropdown-menu-separator"
      className={cn("-mx-1 my-1 h-px bg-border", className)}
      {...props}
    />
  );
}
