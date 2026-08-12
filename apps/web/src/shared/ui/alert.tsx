import { cn } from "@/shared/lib";
import { type VariantProps, cva } from "class-variance-authority";
import type { ComponentProps } from "react";

/**
 * 알림 상자 (#291 1단계).
 *
 * 뱃지와 **같은 tone 이름**을 쓴다. 같은 뜻을 두 어휘로 부르면 화면마다 다르게 적힌다.
 *
 * shadcn 은 `AlertTitle`/`AlertDescription` 으로 나누지만, 이 저장소에서 쓰는 자리는
 * 전부 **한 줄짜리 안내**다. 쓰지 않는 조각을 미리 만들지 않는다 — 필요해지면 그때
 * 더한다.
 *
 * `role="alert"` 는 그대로 둔다. 이것이 없으면 스크린 리더가 오류를 읽지 않는다.
 */
const alertVariants = cva("rounded-lg border px-3 py-2 text-sm", {
  variants: {
    tone: {
      ok: "border-ok/30 bg-ok/12 text-ok",
      danger: "border-danger/30 bg-danger/12 text-danger",
      warn: "border-warn/30 bg-warn/12 text-warn",
      info: "border-info/30 bg-info/12 text-info",
      muted: "border-border bg-surface-muted text-ink-muted",
    },
  },
  defaultVariants: { tone: "danger" },
});

export function Alert({
  className,
  tone,
  ...props
}: ComponentProps<"div"> & VariantProps<typeof alertVariants>) {
  return (
    <div data-slot="alert" role="alert" className={cn(alertVariants({ tone }), className)} {...props} />
  );
}
