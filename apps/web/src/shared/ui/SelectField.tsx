"use client";

import { Children, Fragment, isValidElement } from "react";
import type { ReactNode } from "react";
import { Select as Root, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./select";

/**
 * `<option>` 을 그대로 받는 선택기 (#287).
 *
 * **부르는 쪽을 고치지 않으려고 둔 층이다.** 지금 열 곳 남짓이 네이티브 `<select>` 의
 * 모양(`value` + `onChange(event)` + `<option>` 자식)으로 적혀 있다. 이관(#291)의
 * 첫 걸음에서 그 열 곳을 함께 고치면, 무엇이 깨졌을 때 **선택기 때문인지 화면을
 * 고쳐서인지** 알 수 없게 된다.
 *
 * 그래서 이 PR 은 **껍데기만 바꾼다.** 새로 쓰는 화면은 `SelectRoot`/`SelectItem` 을
 * 직접 쓰면 되고, 이 층은 부르는 쪽이 모두 옮겨간 뒤에 지운다.
 */

/**
 * 빈 문자열 대신 쓰는 값.
 *
 * Radix 는 **빈 문자열을 항목 값으로 받지 않는다** — 그것을 "고르지 않음" 의 내부
 * 표시로 쓰기 때문이다. 그런데 우리 화면들은 `<option value="">전체</option>` 로
 * "조건 없음" 을 표현해 왔다. 여기서 바꿔치기해 두 규약을 잇는다.
 */
const EMPTY = "__codekr_empty__";

interface Option {
  value: string;
  label: ReactNode;
  disabled?: boolean;
}

/**
 * 자식으로 넘어온 `<option>` 들을 읽는다.
 *
 * **조각(`<>…</>`) 안을 들여다본다** (#660). `Children.forEach` 는 조각을 자식 하나로
 * 세고 그 `type` 이 `"option"` 이 아니라, 전에는 **항목이 통째로 사라졌다.** 오류도
 * 없이 빈 선택기가 그려져서 화면을 열기 전까지 몰랐다.
 *
 * 조각은 조건부 렌더에서 자연스럽게 나온다 — `{isAdmin && (<><option/><option/></>)}`.
 * 그러면 **어드민에게만 두 항목이 조용히 사라진다.**
 *
 * `<optgroup>` 은 **지원하지 않고, 대신 알린다.** 안을 펼쳐 붙일 수도 있지만 그러면
 * 묶음 이름이 사라져 서로 다른 묶음의 같은 이름("기본")이 구별되지 않는다 — 조용히
 * 틀린 화면이다. 이 층은 부르는 쪽이 옮겨간 뒤 지울 것이라(#291) 묶음을 제대로
 * 구현하는 자리가 아니다.
 */
function readOptions(children: ReactNode): Option[] {
  const options: Option[] = [];
  Children.forEach(children, (child) => {
    if (!isValidElement(child)) return;
    if (child.type === Fragment) {
      // 조각 안이 또 조각일 수 있다. 배열은 Children 이 이미 펼친다.
      options.push(...readOptions((child.props as { children?: ReactNode }).children));
      return;
    }
    if (child.type !== "option") {
      warnUnsupported(child.type);
      return;
    }
    const props = child.props as { value?: string | number; children?: ReactNode; disabled?: boolean };
    const value = String(props.value ?? "");
    options.push({ value, label: props.children ?? value, disabled: props.disabled });
  });
  return options;
}

/**
 * 모르는 자식은 **버리되 말한다** (#660).
 *
 * 재귀로 조각은 풀렸지만 `<div>`·`<optgroup>` 같은 것은 여전히 사라진다. 조용히
 * 사라지는 것이 이 결함의 본질이었으므로, 같은 모양을 남겨 두지 않는다.
 * 운영에서는 말하지 않는다 — 사용자가 할 수 있는 일이 없다.
 */
function warnUnsupported(type: unknown) {
  if (process.env.NODE_ENV === "production") return;
  const name = typeof type === "string" ? `<${type}>` : String(type);
  console.warn(`SelectField: ${name} 는 항목으로 그려지지 않습니다. <option> 이나 조각으로 감싸세요 (#660).`);
}

interface Props {
  value?: string;
  onChange?: (event: { target: { value: string } }) => void;
  children?: ReactNode;
  disabled?: boolean;
  className?: string;
  "aria-label"?: string;
  id?: string;
}

export function SelectField({ value, onChange, children, disabled, className, ...rest }: Props) {
  const options = readOptions(children);
  const selected = options.find((option) => option.value === (value ?? ""));

  return (
    <Root
      value={value === "" ? EMPTY : value}
      onValueChange={(next) =>
        // 부르는 쪽은 이벤트를 기대한다. 값만 넘기면 열 곳을 전부 고쳐야 한다.
        onChange?.({ target: { value: next === EMPTY ? "" : next } })
      }
      disabled={disabled}
    >
      <SelectTrigger className={className} {...rest}>
        <SelectValue>{selected?.label}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem
            key={option.value}
            value={option.value === "" ? EMPTY : option.value}
            disabled={option.disabled}
          >
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Root>
  );
}
