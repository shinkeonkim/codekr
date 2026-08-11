import { describe, expect, test } from "bun:test";
import { avatarColorOf, avatarInitialOf } from "./Avatar";

describe("기본 아바타 (#116)", () => {
  test("같은 사람은 늘 같은 색이다", () => {
    // 무작위로 고르면 새로고침마다 색이 바뀌어 구분에 아무 도움이 되지 않는다.
    expect(avatarColorOf("풀이왕")).toBe(avatarColorOf("풀이왕"));
  });

  test("다른 사람은 대체로 다른 색이다", () => {
    // 목록에서 사람을 구분하는 것이 아바타를 넣은 이유다.
    const colors = new Set(
      ["가", "나", "다", "라", "마", "바", "사", "아"].map(avatarColorOf),
    );
    expect(colors.size).toBeGreaterThan(1);
  });

  test("첫 글자를 코드 포인트로 자른다", () => {
    expect(avatarInitialOf("풀이왕")).toBe("풀");
    expect(avatarInitialOf("codekr")).toBe("C");
    // 이모지는 두 개의 UTF-16 단위라 charAt 으로 자르면 깨진다.
    expect(avatarInitialOf("🚀rocket")).toBe("🚀");
  });

  test("닉네임이 비어도 무언가는 그린다", () => {
    expect(avatarInitialOf("")).toBe("?");
  });
});
