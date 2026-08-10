import { describe, expect, test } from "bun:test";
import { OVERLAY } from "./overlay";

describe("고정 요소의 자리 (#134)", () => {
  test("토스트는 데스크톱에서 우측에 붙는다", () => {
    // 가운데는 눈이 가장 자주 머무는 자리이고 본문 위를 덮는다.
    expect(OVERLAY.toastViewport).toContain("sm:right-4");
    expect(OVERLAY.toastViewport).toContain("sm:items-end");
    expect(OVERLAY.toastViewport).not.toContain("items-center");
  });

  test("좁은 화면에서는 좌우 여백만 두고 가로로 채운다", () => {
    // 우측에 붙이면 글이 서너 줄로 접힌다.
    expect(OVERLAY.toastViewport).toContain("inset-x-4");
    expect(OVERLAY.toastViewport).toContain("items-stretch");
  });

  test("층은 토큰으로 쓴다", () => {
    // 숫자를 화면마다 흩뿌리면 무엇이 무엇 위에 있어야 하는지 알 수 없게 된다.
    expect(OVERLAY.toastViewport).toContain("z-toast");
    expect(OVERLAY.toastViewport).not.toMatch(/z-\d/);
  });
});
