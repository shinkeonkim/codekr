import { cn } from "@/shared/lib";
import type { ComponentProps } from "react";

/**
 * 항목 이름 (#291 1단계).
 *
 * **shadcn 의 `Form` 은 들이지 않았다.** 그것은 `react-hook-form` 을 전제하는데,
 * 지금 폼은 전부 `useState` 다. 폼 라이브러리 도입은 이 이슈보다 크다 (#291 의
 * 판단 지점) — `Label` 만 가져오고 `Field` 는 우리 것으로 남긴다.
 *
 * Radix 의 `Label` 도 쓰지 않는다. 그것이 해 주는 일(누르면 입력으로 초점)은
 * 브라우저가 `<label>` 로 이미 한다 — 의존성을 하나 더 들일 이유가 없다.
 */
export function Label({ className, ...props }: ComponentProps<"label">) {
  return (
    <label
      data-slot="label"
      className={cn(
        "text-sm font-medium text-foreground select-none",
        "peer-disabled:cursor-not-allowed peer-disabled:opacity-70",
        className,
      )}
      {...props}
    />
  );
}
