import { cn } from "@/shared/lib";
import type { ComponentProps } from "react";

/**
 * 상자 (#291 3단계).
 *
 * **shadcn 의 `Card` 를 그대로 가져오지 않았다.** 그쪽 기본은
 * `flex flex-col gap-6 py-6 rounded-xl shadow-sm` 인데, 이 저장소의 상자 마흔일곱
 * 자리는 전부 자기 여백과 배치를 `className` 으로 준다 (`p-5`, `space-y-3`,
 * `grid grid-cols-2`, `divide-y p-0` …).
 *
 * 그 기본을 들이면 **마흔일곱 화면의 배치가 한꺼번에 바뀐다** — `flex flex-col` 이
 * 붙고 `gap-6` 이 끼어든다. `p-5` 는 tailwind-merge 가 정리해 주지만 `flex`·`gap` 은
 * 정리 대상이 아니다. 옮기는 변경과 모양을 바꾸는 변경이 한 diff 에 섞이면 리뷰가
 * 불가능해진다 (#291 이 정한 규칙).
 *
 * 그래서 **평평한 상자를 유지한다.** 여기서 얻는 것은 `cn` 을 거치는 클래스 병합과
 * `data-slot` 이고, 그것은 `Button`(2단계)·`Badge`(1단계)와 같은 방식이다.
 *
 * `CardHeader`·`CardContent` 를 두지 않은 이유도 같다 — 평평한 상자에서 그것들은
 * 아무 일도 하지 않는 감싸개다. 안 쓰는 API 를 먼저 만들면 두 방식이 공존한다.
 */
export function Card({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      data-slot="card"
      className={cn("rounded-card border border-border bg-surface", className)}
      {...props}
    />
  );
}

/**
 * 상자의 제목.
 *
 * `text-sm font-semibold text-ink` 가 **스물여섯 자리에 그대로 적혀 있었다.**
 * 한 자리에서만 정하면 되는 것이 스물여섯 곳에 흩어져 있으면, 제목 크기를 한 번
 * 바꾸는 일이 스물여섯 곳을 고치는 일이 된다.
 *
 * **`div` 가 아니라 `h2` 다.** shadcn 은 `div` 를 쓰지만, 이 자리들은 실제로 화면의
 * 구역 제목이다 — 스크린 리더가 문서를 훑을 때 그것이 목차가 된다.
 */
export function CardTitle({ className, ...props }: ComponentProps<"h2">) {
  return (
    <h2
      data-slot="card-title"
      className={cn("text-sm font-semibold text-ink", className)}
      {...props}
    />
  );
}
