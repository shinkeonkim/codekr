import { describe, expect, it } from "bun:test";
import { activeQuery, toDisplay, toStored } from "./mentionText";

const 불린이 = { id: 42, nickname: "불린이" };

describe("toDisplay", () => {
  it("저장 표기를 이름으로 되돌린다", () => {
    // 편집기를 열었을 때 저장 표기가 그대로 보이면 그것이 무엇인지 알 수 없다 (#214).
    expect(toDisplay("@{u:42} 안녕", [불린이])).toBe("@불린이 안녕");
  });

  it("이름표가 없으면 표기를 남기지 않는다", () => {
    expect(toDisplay("@{u:999} 안녕", [])).toBe("@알 수 없는 사용자 안녕");
  });
});

describe("toStored", () => {
  it("고른 사람만 표기로 바꾼다", () => {
    // 손으로 친 것은 그냥 글자다 — 동명이인에서 엉뚱한 사람을 가리키지 않는다.
    expect(toStored("@불린이 와 @아무개", [불린이])).toBe("@{u:42} 와 @아무개");
  });

  it("긴 이름부터 바꾼다", () => {
    // 짧은 것을 먼저 바꾸면 긴 이름의 앞부분만 잘려 나간다.
    const 김철 = { id: 1, nickname: "김철" };
    const 김철수 = { id: 2, nickname: "김철수" };
    expect(toStored("@김철수 님", [김철, 김철수])).toBe("@{u:2} 님");
  });
});

describe("activeQuery", () => {
  it("커서 앞의 질의를 읽는다", () => {
    expect(activeQuery("안녕 @불린", 6)).toBe("불린");
    expect(activeQuery("@", 1)).toBe("");
  });

  it("공백이 들어가면 끝난 것으로 본다", () => {
    expect(activeQuery("@불린 이", 5)).toBeNull();
  });

  it("이메일은 멘션이 아니다", () => {
    expect(activeQuery("me@codekr", 9)).toBeNull();
  });
});
