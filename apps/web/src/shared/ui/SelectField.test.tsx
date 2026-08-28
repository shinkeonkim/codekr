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
      배열로 돌려준다. **조각으로 감싸도 이제 된다** (#660).

      이 시험을 쓰다가 결함을 잡았다 — `Children.forEach` 는 조각을 자식 하나로 세고
      그 `type` 이 `"option"` 이 아니라 **항목이 통째로 비었다.** 오류 없이 빈 선택기가
      그려져 화면을 열기 전까지 몰랐다. 아래에 그것을 붙잡는 시험이 있다.
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

  /*
      **조각으로 감싼 항목이 그려진다** (#660).

      이것이 이 결함의 핵심이다. 조각은 조건부 렌더에서 자연스럽게 나오고
      (`{isAdmin && (<>…</>)}`), 그러면 **어드민에게만 항목이 조용히 사라진다.**
  */
  test("조각으로 감싼 항목도 그려진다", () => {
    render(
      <SelectField value="SILVER" aria-label="티어">
        <option value="">전체</option>
        <>
          <option value="BRONZE">브론즈</option>
          <option value="SILVER">실버</option>
        </>
      </SelectField>,
    );

    expect(screen.getByRole("combobox", { name: "티어" }).textContent).toContain("실버");
  });

  /** 조건부 렌더가 만드는 실제 모양 — 조각이 `false` 와 섞여 들어온다. */
  test("조건부로 붙인 조각도 그려진다", () => {
    const isAdmin = true;
    render(
      <SelectField value="HIDDEN" aria-label="상태">
        <option value="">전체</option>
        {isAdmin && (
          <>
            <option value="DRAFT">초안</option>
            <option value="HIDDEN">비공개</option>
          </>
        )}
      </SelectField>,
    );

    expect(screen.getByRole("combobox", { name: "상태" }).textContent).toContain("비공개");
  });

  /** 조각 안의 조각까지 푼다. 한 겹만 풀면 같은 결함이 한 겹 아래로 옮겨간 것뿐이다. */
  test("겹친 조각도 푼다", () => {
    render(
      <SelectField value="SILVER" aria-label="티어">
        <>
          <option value="BRONZE">브론즈</option>
          <>
            <option value="SILVER">실버</option>
          </>
        </>
      </SelectField>,
    );

    expect(screen.getByRole("combobox", { name: "티어" }).textContent).toContain("실버");
  });

  /*
      **모르는 자식은 버리되 말한다.**

      재귀로 조각은 풀렸지만 `<div>`·`<optgroup>` 은 여전히 사라진다. 조용히
      사라지는 것이 이 결함의 본질이었으므로 같은 모양을 남겨 두지 않는다.
  */
  test("모르는 자식은 개발 중에 경고한다", () => {
    const warnings: string[] = [];
    const original = console.warn;
    console.warn = (...args: unknown[]) => warnings.push(String(args[0]));
    try {
      render(
        <SelectField value="BRONZE" aria-label="티어">
          <option value="BRONZE">브론즈</option>
          <optgroup label="묶음">
            <option value="SILVER">실버</option>
          </optgroup>
        </SelectField>,
      );
    } finally {
      console.warn = original;
    }

    expect(warnings.some((line) => line.includes("optgroup"))).toBe(true);
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
