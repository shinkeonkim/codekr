/**
 * 칸의 정렬과 숨김을 **한 곳에서** 정한다 (#193).
 *
 * 전에는 머리글(`thead`)과 값(`tbody`)이 같은 조건식을 각자 들고 있었다. 한쪽만 고치면
 * 머리글과 값의 축이 어긋나는데, 그것이 이 이슈가 지적한 증상이었다.
 */

export type Align = "left" | "center" | "right";

/** 이 폭보다 좁으면 열을 감춘다. */
export type Breakpoint = "sm" | "lg";

// Tailwind 는 클래스 이름을 소스에서 그대로 찾아 쓴다 — `sm:table-cell` 처럼 조립하면
// 빌드 결과에서 빠진다. 그래서 온전한 이름을 표로 둔다.
const ALIGN_CLASS: Record<Align, string> = {
  left: "",
  center: "text-center",
  right: "text-right",
};

const HIDE_CLASS: Record<Breakpoint, string> = {
  sm: "hidden sm:table-cell",
  lg: "hidden lg:table-cell",
};

export function cellClass(column: {
  align?: Align;
  hideBelow?: Breakpoint;
  width?: string;
}): string {
  return [
    ALIGN_CLASS[column.align ?? "left"],
    column.hideBelow ? HIDE_CLASS[column.hideBelow] : "",
    // 폭은 부르는 쪽이 온전한 클래스로 넘긴다. 여기서 조립하면 Tailwind 가 못 찾는다.
    column.width ?? "",
  ]
    .filter(Boolean)
    .join(" ");
}
