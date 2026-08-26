import { describe, expect, test } from "bun:test";
import { render, screen } from "@testing-library/react";
import { SelectField } from "./SelectField";

/**
 * 선택기 (#287, #646).
 *
 * **이 파일이 있는 첫 이유는 도구를 고르기 위해서였다.** #646 은 happy-dom 과 jsdom
 * 중 무엇을 쓸지 정하지 못한 채 열렸고, 판단 근거를 "Radix 를 쓰는 컴포넌트가 실제로
 * 그려지는지 확인해야 안다" 로 적어 두었다. `SelectField` 가 Radix 위에 서 있으므로
 * 여기가 그 확인이다 — **그리고 확인한 뒤에도 남는다.**
 *
 * 두 번째 이유는 이 층이 **두 규약을 잇고 있다**는 것이다. 부르는 쪽은 네이티브
 * `<select>` 의 모양(`value` + `<option>`)으로 적혀 있고 안은 Radix 다. 그 사이의
 * 변환은 눈에 보이지 않아서, 깨져도 화면을 열어 보기 전까지 모른다.
 */
describe("SelectField (#287)", () => {
  /*
      **배열로 돌려준다. 조각(`<>…</>`)으로 감싸면 안 된다.**

      이 시험을 쓰다가 알았다 — `readOptions` 는 `Children.forEach` 로 자식을 훑는데,
      조각은 자식 하나로 세어지고 그 `type` 이 `"option"` 이 아니라 **항목이 통째로
      비어 버린다.** 오류 없이 빈 선택기가 그려져서 화면을 열기 전까지 모른다.
      이 PR 의 범위를 넘으므로 #660 으로 남겼다.
  */
  function tiers() {
    return [
      <option key="" value="">전체</option>,
      <option key="BRONZE" value="BRONZE">브론즈</option>,
      <option key="SILVER" value="SILVER">실버</option>,
    ];
  }

  test("고른 값의 이름을 보여 준다", () => {
    render(
      <SelectField value="SILVER" aria-label="티어">
        {tiers()}
      </SelectField>,
    );

    expect(screen.getByRole("combobox", { name: "티어" }).textContent).toContain("실버");
  });

  /*
      **빈 문자열이 이 층의 가장 미끄러운 곳이다.**

      Radix 는 빈 문자열을 항목 값으로 받지 않는다 — "고르지 않음" 의 내부 표시로
      쓰기 때문이다. 그런데 우리 화면들은 `<option value="">전체</option>` 로
      "조건 없음" 을 말해 왔다. 이 층이 그 둘을 바꿔치기해 잇는데, 그것이 깨지면
      **필터의 "전체" 가 사라지거나 Radix 가 던진다.**
  */
  test("빈 값도 고를 수 있는 항목이다", () => {
    render(
      <SelectField value="" aria-label="티어">
        {tiers()}
      </SelectField>,
    );

    expect(screen.getByRole("combobox", { name: "티어" }).textContent).toContain("전체");
  });

  test("끌 수 있다", () => {
    render(
      <SelectField value="BRONZE" aria-label="티어" disabled>
        {tiers()}
      </SelectField>,
    );

    expect(screen.getByRole("combobox", { name: "티어" }).hasAttribute("disabled")).toBe(true);
  });

  /** `<option>` 이 아닌 자식은 무시한다 — 조건부 렌더가 `false`·`null` 을 흘린다. */
  test("option 이 아닌 자식은 무시한다", () => {
    render(
      <SelectField value="BRONZE" aria-label="티어">
        {null}
        {false}
        <option value="BRONZE">브론즈</option>
      </SelectField>,
    );

    expect(screen.getByRole("combobox", { name: "티어" }).textContent).toContain("브론즈");
  });
});
