import { cn } from "@/shared/lib";
import { type VariantProps, cva } from "class-variance-authority";
import type { ComponentProps } from "react";

/**
 * 버튼 (#291 2단계).
 *
 * **혼자 가는 단계다.** 쓰는 곳이 58개 파일이라, 다른 것과 묶으면 무엇이 무엇을
 * 깨뜨렸는지 알 수 없다.
 *
 * **variant 넷을 이름까지 그대로 둔다.** shadcn 기본은 `default`/`destructive`/
 * `outline`/`secondary`/`ghost`/`link` 인데, 이 저장소는 `primary`/`secondary`/
 * `ghost`/`danger` 를 쓴다. 이름을 바꾸면 58개 파일이 함께 바뀌고, **옮기는 변경과
 * 고치는 변경이 한 diff 에 섞인다** — 그러면 리뷰가 불가능해진다.
 *
 * 색도 그대로다. 여기서 바뀌는 것은 **어떻게 적히는가**뿐이다 — `cva` 로 옮기면
 * variant 가 타입으로 강제되고, 클래스 병합이 `cn`(tailwind-merge) 을 거쳐
 * `className` 으로 준 값이 **실제로 이긴다.**
 */
const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50",
  {
    variants: {
      variant: {
        primary: "bg-brand text-brand-ink hover:opacity-90",
        secondary: "border border-border bg-surface text-ink hover:bg-surface-muted",
        ghost: "text-ink-muted hover:text-ink hover:bg-surface-muted",
        danger: "border border-danger/40 text-danger hover:bg-danger/10",
      },
    },
    defaultVariants: { variant: "primary" },
  },
);

export type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;

export function Button({
  className,
  variant,
  ...props
}: ComponentProps<"button"> & VariantProps<typeof buttonVariants>) {
  return (
    <button data-slot="button" className={cn(buttonVariants({ variant }), className)} {...props} />
  );
}
