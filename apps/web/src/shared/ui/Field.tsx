"use client";

import { cloneElement, isValidElement, useId } from "react";
import type { ReactNode } from "react";
import { Label } from "./label";

/**
 * 이름표 + 오류를 한 묶음으로 두는 우리 것 (#291).
 *
 * shadcn 의 `Form` 은 `react-hook-form` 을 전제하므로 들이지 않았다. 이름표만
 * `Label` 로 갈아 끼운다 — 같은 것을 두 곳에 적지 않기 위해서다.
 *
 * **`<label>` 로 감싸지 않는다** (#306). 전에는 자식을 통째로 `<label>` 안에 넣었는데,
 * 자식이 네이티브 `<select>` 이던 시절에는 자연스러웠지만 지금은 Radix 의 `<button>`
 * 이다. `<label>` 은 눌리면 자기가 가리키는 컨트롤에 활성화를 넘기므로 **한 번의 탭이
 * 두 번의 활성화**가 될 수 있다. 게다가 `Label` 자체가 `<label>` 이라 **중첩된
 * label** 이었다 — 올바른 HTML 이 아니고 브라우저마다 다르게 해석한다.
 *
 * 대신 `htmlFor`/`id` 로 잇는다. 눌러서 초점이 가는 동작은 그대로다.
 */
export function Field({
  label,
  error,
  children,
  htmlFor,
}: {
  label: string;
  error?: string;
  children: ReactNode;
  /** 자식이 id 를 스스로 정하는 경우. 보통은 비워 두면 여기서 만든다. */
  htmlFor?: string;
}) {
  const generated = useId();
  const child = isValidElement<{ id?: string }>(children) ? children : null;
  const id = child?.props.id ?? htmlFor ?? generated;

  return (
    <div className="block space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {/* 자식이 하나의 원소면 id 를 심어 이름표와 잇는다. 여럿이면 그대로 둔다. */}
      {child ? cloneElement(child, { id }) : children}
      {error ? <span className="block text-xs text-danger">{error}</span> : null}
    </div>
  );
}
