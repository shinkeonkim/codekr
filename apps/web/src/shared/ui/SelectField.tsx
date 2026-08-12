"use client";

import { Children, isValidElement } from "react";
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

/** 자식으로 넘어온 `<option>` 들을 읽는다. `<optgroup>` 은 아직 쓰는 곳이 없다. */
function readOptions(children: ReactNode): Option[] {
  const options: Option[] = [];
  Children.forEach(children, (child) => {
    if (!isValidElement(child) || child.type !== "option") return;
    const props = child.props as { value?: string | number; children?: ReactNode; disabled?: boolean };
    const value = String(props.value ?? "");
    options.push({ value, label: props.children ?? value, disabled: props.disabled });
  });
  return options;
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
