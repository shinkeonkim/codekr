import { describe, expect, test } from "bun:test";
import { codeFence, linePrefix, link, orderedList, wrap } from "./editorCommands";

/**
 * 편집기 버튼 (#388).
 *
 * **커서 자리를 다루는 코드는 눈으로 읽어서 맞는지 알 수 없다.** 화면에서 확인하려면
 * 매번 눌러 봐야 하고, 그러면 고치는 사람이 확인을 건너뛴다.
 */
describe("굵게·인라인 코드", () => {
  test("고른 글자를 감싼다", () => {
    const result = wrap("가나다", 1, 2, "**");

    expect(result.text).toBe("가**나**다");
    // 감싼 글자가 그대로 선택돼 있어야 다시 바꿀 수 있다.
    expect(result.text.slice(result.selectionStart, result.selectionEnd)).toBe("나");
  });

  test("아무것도 안 골랐으면 표시 사이에 커서를 둔다", () => {
    // 빈 `****` 를 남기고 커서를 끝에 두면 사용자가 화살표로 되돌아와야 한다.
    const result = wrap("가나", 1, 1, "**");

    expect(result.text).toBe("가****나");
    expect(result.selectionStart).toBe(3);
    expect(result.selectionEnd).toBe(3);
  });
});

describe("목록·제목", () => {
  test("고른 줄 전부에 붙는다", () => {
    const result = linePrefix("하나\n둘\n셋", 0, 5, "- ");

    expect(result.text).toBe("- 하나\n- 둘\n셋");
  });

  test("줄 중간에서 골라도 줄 처음부터 붙는다", () => {
    // 목록 표시가 줄 가운데 들어가면 마크다운이 아니라 글자가 된다.
    const result = linePrefix("하나\n둘", 5, 5, "- ");

    expect(result.text).toBe("하나\n- 둘");
  });
});

describe("코드 블록", () => {
  test("고른 코드를 감싸고 앞뒤에 빈 줄을 둔다", () => {
    // 앞 문단에 붙어 있으면 한 덩어리로 읽혀 블록이 되지 않는다.
    const result = codeFence("설명", 3, 3);

    expect(result.text).toBe("설명\n\n```\n\n```\n");
  });

  test("이미 빈 줄이 있으면 더 넣지 않는다", () => {
    const result = codeFence("설명\n\n", 5, 5);

    expect(result.text).toBe("설명\n\n```\n\n```\n");
  });

  test("고른 것이 있으면 그것을 감싸고 선택을 유지한다", () => {
    const result = codeFence("print(1)", 0, 8);

    expect(result.text).toBe("```\nprint(1)\n```\n");
    expect(result.text.slice(result.selectionStart, result.selectionEnd)).toBe("print(1)");
  });

  test("빈 블록이면 그 안에 커서가 간다", () => {
    // 바로 코드를 붙여 넣을 수 있어야 한다.
    const result = codeFence("", 0, 0);

    expect(result.text.slice(0, result.selectionStart)).toBe("```\n");
    expect(result.selectionStart).toBe(result.selectionEnd);
  });
});

describe("번호 목록", () => {
  test("줄마다 번호가 올라간다", () => {
    const result = orderedList("하나\n둘\n셋", 0, 8);

    expect(result.text).toBe("1. 하나\n2. 둘\n3. 셋");
  });

  test("선택이 줄 첫머리에서 끝나면 그 줄은 넣지 않는다", () => {
    // `linePrefix` 와 같은 규칙이다 — 다르면 버튼마다 다르게 동작한다.
    const result = orderedList("하나\n둘\n셋", 0, 3);

    expect(result.text).toBe("1. 하나\n둘\n셋");
  });
});

describe("링크", () => {
  test("고른 글자가 이름이 되고 커서는 주소 자리에 선다", () => {
    const result = link("여기를 보세요", 0, 3);

    expect(result.text).toBe("[여기를]() 보세요");
    // 커서가 괄호 안이어야 주소를 바로 칠 수 있다.
    expect(result.selectionStart).toBe("[여기를](".length);
    expect(result.selectionEnd).toBe(result.selectionStart);
  });

  test("아무것도 안 골랐으면 이름 자리를 채워 둔다", () => {
    // 빈 `[]()` 를 남기면 무엇을 채워야 하는지가 안 보인다.
    const result = link("", 0, 0);

    expect(result.text).toBe("[링크]()");
  });
});
