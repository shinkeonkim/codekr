import { describe, expect, test } from "bun:test";
import { findErrorLocation } from "./errorLocation";

/**
 * 컴파일 오류가 어느 파일의 이야기인가 (#457, #498).
 *
 * 파일이 여럿이면 **어느 탭을 열어야 하는지**를 말해 주지 않는 한, 사용자는 오류 문자열을
 * 눈으로 훑어야 한다. 그리고 그 문자열의 모양은 언어마다 다르다.
 */
describe("컴파일 오류의 자리", () => {
  const files = ["Main.java", "Helper.java"];

  test("자바", () => {
    expect(
      findErrorLocation("Helper.java:17: error: ';' expected", files),
    ).toEqual({
      file: "Helper.java",
      line: 17,
    });
  });

  test("파이썬", () => {
    expect(
      findErrorLocation('  File "helper.py", line 3\n    def add(', [
        "main.py",
        "helper.py",
      ]),
    ).toEqual({
      file: "helper.py",
      line: 3,
    });
  });

  test("C++", () => {
    expect(
      findErrorLocation("helper.cpp:5:1: error: expected ';'", [
        "main.cpp",
        "helper.cpp",
      ]),
    ).toEqual({
      file: "helper.cpp",
      line: 5,
    });
  });

  test("Go — 경로가 앞에 붙어도 찾는다", () => {
    expect(
      findErrorLocation("./helper.go:4:6: syntax error", [
        "main.go",
        "helper.go",
      ]),
    ).toEqual({
      file: "helper.go",
      line: 4,
    });
  });

  test("먼저 나온 파일을 고른다 — 컴파일러는 첫 오류를 먼저 말한다", () => {
    const message = "Helper.java:17: error: x\nMain.java:3: error: y";
    expect(findErrorLocation(message, files)?.file).toBe("Helper.java");
  });

  test("**모르면 아무 말도 하지 않는다**", () => {
    // 틀린 자리를 가리키는 것은 침묵보다 나쁘다 — 사용자가 멀쩡한 줄을 고치게 된다.
    expect(findErrorLocation("Segmentation fault", files)).toBeNull();
    expect(findErrorLocation("Other.java:1: error", files)).toBeNull();
  });

  test("줄 번호가 없어도 파일은 말해 준다", () => {
    expect(
      findErrorLocation("Helper.java: 인코딩을 읽을 수 없습니다", files),
    ).toEqual({
      file: "Helper.java",
      line: null,
    });
  });
});
