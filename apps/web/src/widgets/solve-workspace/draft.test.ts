import { describe, expect, test } from "bun:test";
import { draftKey, initialSource } from "./draft";

/**
 * 이 언어에서 무엇으로 시작하는가 (#383).
 *
 * **제출한 코드가 자기가 쓴 것이 아니었다는 사고**가 이 판단에서 났다. 틀린 판정이
 * 기록으로 남고 그 위에 정답률·랭킹·스트릭이 쌓이므로, 재채점(#107)으로도 풀리지 않는다.
 */
describe("초안과 템플릿", () => {
  test("초안이 있으면 초안이 이긴다", () => {
    expect(initialSource("내가 쓴 코드", "템플릿")).toBe("내가 쓴 코드");
  });

  test("초안이 없으면 템플릿", () => {
    expect(initialSource(null, "템플릿")).toBe("템플릿");
  });

  test("**빈 초안도 초안이다**", () => {
    /*
      전에는 빈 값을 저장하지 않아서, 코드를 전부 지우고 나가면 다시 들어왔을 때
      옛 초안이 되살아났다. **지운 것도 사용자가 한 일이다** — 템플릿으로 되돌리는
      것은 사용자가 시키지 않은 일이다.
    */
    expect(initialSource("", "템플릿")).toBe("");
  });

  test("문제와 언어마다 따로 남는다", () => {
    // 같은 문제를 두 언어로 풀 때 서로 덮어쓰면 안 된다.
    expect(draftKey("two-sum", "python:3.12")).not.toBe(
      draftKey("two-sum", "kotlin:2.0"),
    );
    expect(draftKey("two-sum", "python:3.12")).not.toBe(
      draftKey("a-plus-b", "python:3.12"),
    );
  });
});
