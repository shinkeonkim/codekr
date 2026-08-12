import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * 클래스 이름을 합친다 (#287).
 *
 * shadcn 컴포넌트가 전제하는 도구다. `clsx` 는 조건부 클래스를 펴 주고,
 * `twMerge` 는 **뒤에 온 것이 이기게** 만든다 — `px-2` 와 `px-4` 가 함께 있으면
 * CSS 는 소스 순서로 이기는데, 그 순서는 부르는 쪽이 정할 수 없다.
 *
 * 이것이 없으면 `className` 으로 넘긴 값이 컴포넌트 기본값에 지는 일이 생기고,
 * 그때마다 `!important` 를 붙이게 된다.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
