import { describe, expect, test } from "bun:test";
import { isSafeUrl } from "./Markdown";

/**
 * 링크 주소 검사 (#137).
 *
 * 렌더러 자체는 React 엘리먼트를 만들므로 `<script>` 가 실행될 길이 없다.
 * **남은 구멍은 링크 주소 하나**라 그것만 따로 시험한다.
 */
describe("링크 주소", () => {
  test("javascript 는 링크로 만들지 않는다", () => {
    expect(isSafeUrl("javascript:alert(1)")).toBe(false);
    // 대소문자·공백으로 우회할 수 없어야 한다.
    expect(isSafeUrl("JavaScript:alert(1)")).toBe(false);
    expect(isSafeUrl(" javascript:alert(1)")).toBe(false);
  });

  test("data 와 vbscript 도 막는다", () => {
    expect(isSafeUrl("data:text/html;base64,PHNjcmlwdD4=")).toBe(false);
    expect(isSafeUrl("vbscript:msgbox(1)")).toBe(false);
  });

  test("http 와 https 는 통과한다", () => {
    expect(isSafeUrl("https://codekr.kr")).toBe(true);
    expect(isSafeUrl("http://localhost:3000")).toBe(true);
  });

  test("사이트 안의 경로는 통과한다", () => {
    expect(isSafeUrl("/problems/two-sum")).toBe(true);
  });

  test("프로토콜 상대 주소는 막는다", () => {
    // //evil.com 은 현재 프로토콜로 외부에 붙는다.
    expect(isSafeUrl("//evil.com")).toBe(false);
  });
});
