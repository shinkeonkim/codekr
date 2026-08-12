import { cn } from "@/shared/lib";
import { type VariantProps, cva } from "class-variance-authority";
import type { ComponentProps } from "react";

/**
 * 상태 표시 (#291 1단계).
 *
 * **우리 tone 다섯을 그대로 유지한다.** shadcn 기본은 `default`/`secondary`/
 * `destructive`/`outline` 인데, 이 저장소의 뱃지는 **판정과 상태**를 말한다 —
 * 정답(ok)·오답(danger)·대기(warn)·정보(info)·그 외(muted). 이름을 shadcn 것으로
 * 바꾸면 화면에서 `tone="destructive"` 라고 적게 되고, 그것은 "오답" 보다 멀다.
 *
 * `cva` 로 옮긴 것은 얻는 것이 있어서다 — variant 가 타입으로 강제되고, 뒤에 올
 * `Button`(#291 2단계)과 같은 방식으로 적힌다.
 */
const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      tone: {
        ok: "border-ok/30 bg-ok/12 text-ok",
        danger: "border-danger/30 bg-danger/12 text-danger",
        warn: "border-warn/30 bg-warn/12 text-warn",
        info: "border-info/30 bg-info/12 text-info",
        muted: "border-border bg-surface-muted text-ink-muted",
      },
    },
    defaultVariants: { tone: "muted" },
  },
);

export type BadgeTone = NonNullable<VariantProps<typeof badgeVariants>["tone"]>;

export function Badge({
  className,
  tone,
  ...props
}: ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ tone }), className)} {...props} />;
}
