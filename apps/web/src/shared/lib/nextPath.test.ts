import { describe, expect, test } from "bun:test";
import { safeNextPath, toNextParam } from "./nextPath";

/**
 * 로그인 후 돌아갈 경로 (#113).
 *
 * 여기서 막지 못하면 오픈 리다이렉트가 된다 — 공격자가 `?next=https://evil.example`
 * 링크를 뿌려 **로그인 직후** 남의 사이트로 보낼 수 있다. 로그인 직후라는 점이 나쁘다:
 * 사용자는 방금 우리 사이트에 자격증명을 넣었으므로 이어지는 화면을 믿는다.
 */
describe("safeNextPath", () => {
  test("같은 출처의 경로는 그대로 쓴다", () => {
    expect(safeNextPath("/users/%EA%B4%80%EB%A6%AC%EC%9E%90")).toBe("/users/%EA%B4%80%EB%A6%AC%EC%9E%90");
    expect(safeNextPath("/submissions/12?tab=code")).toBe("/submissions/12?tab=code");
  });

  test("값이 없으면 기본 경로", () => {
    expect(safeNextPath(null)).toBe("/");
    expect(safeNextPath(undefined)).toBe("/");
    expect(safeNextPath("")).toBe("/");
  });

  test("외부 주소는 거부한다", () => {
    expect(safeNextPath("https://evil.example")).toBe("/");
    expect(safeNextPath("http://evil.example/path")).toBe("/");
    expect(safeNextPath("javascript:alert(1)")).toBe("/");
  });

  test("프로토콜 상대 주소도 거부한다", () => {
    // //evil.example 은 현재 스킴을 그대로 써서 외부로 나간다. 슬래시로 시작해 통과하기 쉽다.
    expect(safeNextPath("//evil.example")).toBe("/");
    expect(safeNextPath("//evil.example/path")).toBe("/");
  });

  test("역슬래시가 섞인 주소도 거부한다", () => {
    // 일부 브라우저가 \ 를 / 로 해석해 /\evil.example 이 //evil.example 처럼 동작한다.
    expect(safeNextPath("/\\evil.example")).toBe("/");
    expect(safeNextPath("\\\\evil.example")).toBe("/");
  });
});

describe("toNextParam", () => {
  test("경로와 쿼리를 합쳐 인코딩한다", () => {
    expect(toNextParam("/submissions", "?page=1")).toBe("%2Fsubmissions%3Fpage%3D1");
  });

  test("쿼리가 없으면 경로만", () => {
    expect(toNextParam("/settings", "")).toBe("%2Fsettings");
  });
});
