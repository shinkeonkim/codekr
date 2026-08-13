"use client";

import { useId } from "react";
import type { ReactNode } from "react";
import { Checkbox } from "./checkbox";
import { Label } from "./label";

/**
 * 체크박스 + 옆에 붙는 이름표 (#318).
 *
 * **`Field` 로 하지 않는 이유가 둘이다.**
 *
 * 하나는 모양이다 — `Field` 는 이름표가 **위**에 오는데 체크박스는 **옆**이다.
 * 한 부품으로 둘 다 하려면 방향 옵션이 생기고, 그 옵션은 부르는 쪽마다 다르게
 * 쓰인다.
 *
 * 둘은 감싸기다 — `<label>` 로 감싸면 그 안의 `<button>`(Radix 의 체크박스가 그것이다)이
 * **한 번의 탭에 두 번 활성화**될 수 있다 (#306 에서 선택기가 겪은 것). 그래서 여기서도
 * `htmlFor`/`id` 로만 잇는다.
 */
export function CheckboxField({
  label,
  checked,
  onCheckedChange,
  disabled,
  className = "",
}: {
  label: ReactNode;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  disabled?: boolean;
  className?: string;
}) {
  const id = useId();

  return (
    <div className={`flex items-start gap-2 ${className}`}>
      <Checkbox
        id={id}
        checked={checked}
        disabled={disabled}
        // Radix 는 미결정 상태를 위해 boolean 이 아닌 값을 줄 수 있다. 우리는 두 값만 쓴다.
        onCheckedChange={(next) => onCheckedChange(next === true)}
        className="mt-0.5"
      />
      <Label htmlFor={id} className="cursor-pointer font-normal">
        {label}
      </Label>
    </div>
  );
}
