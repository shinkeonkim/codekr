import { cn } from "@/shared/lib";
import type { ComponentProps } from "react";

/**
 * 입력 칸 (#291 1단계). shadcn/ui 를 이 저장소 토큰에 맞춰 들여왔다.
 *
 * **모양은 그대로다.** 이관 PR 에서 모양까지 고치면 무엇이 달라졌을 때 옮겨서인지
 * 고쳐서인지 알 수 없다 (#291 의 "옮기는 PR 과 고치는 PR 을 나눈다").
 *
 * 바뀐 것은 두 가지다 — `cn()` 으로 합치므로 `className` 이 기본값을 이기고,
 * 유효하지 않은 값(`aria-invalid`)일 때의 표시가 생겼다.
 */
export function Input({ className, type, ...props }: ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "w-full rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground outline-none",
        "placeholder:text-muted-foreground focus:border-primary",
        "disabled:cursor-not-allowed disabled:opacity-50",
        "file:mr-3 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground",
        "aria-invalid:border-destructive",
        className,
      )}
      {...props}
    />
  );
}
