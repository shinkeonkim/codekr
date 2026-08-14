import { describe, expect, test } from "bun:test";
import { type ProfileDraft, canSave, changes, cleanBio, problems } from "./draft";

const saved: ProfileDraft = { displayName: "김신건", bio: "안녕하세요" };
const draft = (over: Partial<ProfileDraft>): ProfileDraft => ({ ...saved, ...over });

describe("무엇을 보낼 것인가 (#581)", () => {
  test("바꾼 것만 담는다", () => {
    // 전부 보내면 항목이 늘었을 때 옛 화면이 새 항목을 지운다 (#104 의 규칙).
    expect(changes(draft({ displayName: "코더" }), saved)).toEqual({ displayName: "코더" });
    expect(changes(draft({ bio: "반갑습니다" }), saved)).toEqual({ bio: "반갑습니다" });
  });

  test("둘 다 바꿨으면 둘 다 담는다 — 한 번의 요청이다", () => {
    const both = changes(draft({ displayName: "코더", bio: "반갑습니다" }), saved);
    expect(both).toEqual({ displayName: "코더", bio: "반갑습니다" });
  });

  test("안 바꿨으면 비어 있다", () => {
    expect(changes(saved, saved)).toEqual({});
  });

  test("공백만 더한 것은 바꾼 것이 아니다", () => {
    // 서버가 다듬으므로, 이것을 바뀐 것으로 세면 저장 뒤에도 "바뀐 것 있음" 이 남는다.
    expect(changes(draft({ displayName: "  김신건  " }), saved)).toEqual({});
    expect(changes(draft({ bio: "안녕하세요  \n" }), saved)).toEqual({});
  });

  test("소개를 비우는 것은 바꾼 것이다", () => {
    // 지우는 일이 저장되지 않으면 사용자는 지웠다고 믿고 나간다.
    expect(changes(draft({ bio: "" }), saved)).toEqual({ bio: "" });
  });

  test("서버와 같은 규칙으로 다듬는다", () => {
    expect(cleanBio("  한 줄  \n  두 줄   \n\n")).toBe("한 줄\n  두 줄");
  });
});

describe("무엇을 막을 것인가 (#581)", () => {
  test("이름이 너무 짧으면 막는다", () => {
    expect(problems(draft({ displayName: "가" }), saved).displayName).toContain("2자 이상");
  });

  test("이름이 너무 길면 막는다", () => {
    expect(problems(draft({ displayName: "가".repeat(31) }), saved).displayName).toBeDefined();
  });

  test("소개가 상한을 넘으면 막는다", () => {
    expect(problems(draft({ bio: "가".repeat(101) }), saved).bio).toContain("100자");
  });

  test("안 바꾼 칸은 보지 않는다", () => {
    // 옛 규칙으로 만든 한 글자 이름을 가진 사람이 소개만 고치는 것까지 막을 이유는 없다.
    const legacy: ProfileDraft = { displayName: "가", bio: "" };
    expect(problems({ displayName: "가", bio: "새 소개" }, legacy)).toEqual({});
  });
});

describe("저장 단추 (#581)", () => {
  test("바꾼 것이 없으면 못 누른다", () => {
    expect(canSave(saved, saved)).toBe(false);
  });

  test("바꾼 것이 있으면 누를 수 있다", () => {
    expect(canSave(draft({ bio: "새 소개" }), saved)).toBe(true);
  });

  test("규칙을 어기면 못 누른다", () => {
    expect(canSave(draft({ displayName: "가" }), saved)).toBe(false);
  });

  test("한쪽이 잘못이면 다른 쪽이 멀쩡해도 못 누른다", () => {
    // 한 번의 요청으로 나가므로, 절반만 저장되는 일이 생기면 안 된다.
    expect(canSave(draft({ displayName: "가", bio: "새 소개" }), saved)).toBe(false);
  });
});
