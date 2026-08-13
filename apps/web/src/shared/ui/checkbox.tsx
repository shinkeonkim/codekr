"use client";

import { cn } from "@/shared/lib";
import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import { CheckIcon } from "lucide-react";
import type { ComponentProps } from "react";

/**
 * 체크박스 (#318).
 *
 * **#291 의 1단계("잎")에서 빠졌던 것이다.** 선택기는 #287 로 다 옮겼는데 체크박스만
 * 날 `<input>` 으로 남아, 대회 화면에서 눈에 띄는 시스템 UI 가 이것 하나였다.
 * 체크박스는 운영체제마다 모양이 다르고, 다크 모드에서 흰 네모가 그대로 뜬다 (#206).
 */
export function Checkbox({ className, ...props }: ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      data-slot="checkbox"
      className={cn(
        "peer size-4 shrink-0 rounded-[4px] border border-border bg-surface shadow-xs transition",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand/40",
        "data-[state=checked]:border-brand data-[state=checked]:bg-brand data-[state=checked]:text-brand-ink",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center text-current">
        <CheckIcon className="size-3.5" />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}
