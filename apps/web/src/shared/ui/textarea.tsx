import { cn } from "@/shared/lib";
import type { ComponentProps } from "react";

/**
 * 여러 줄 입력 (#291 1단계).
 *
 * **고정폭 글꼴이 기본이다.** 이 저장소에서 여러 줄을 입력하는 자리는 문제 지문·
 * 테스트케이스·소스 코드처럼 **줄과 칸이 뜻을 갖는 것**들이다. shadcn 기본값은
 * 비례 글꼴이라 여기서만 우리 것을 지킨다.
 */
export function Textarea({ className, ...props }: ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "w-full rounded-lg border border-input bg-background px-3 py-2 font-mono text-sm text-foreground outline-none",
        "placeholder:text-muted-foreground focus:border-primary",
        "disabled:cursor-not-allowed disabled:opacity-50",
        "aria-invalid:border-destructive",
        className,
      )}
      {...props}
    />
  );
}
