import { describe, expect, test } from "bun:test";
import { OVERLAY } from "./overlay";

/**
 * 고정 요소의 자리 (#134).
 *
 * 토스트가 sonner 로 옮겨가면서(#291 5단계) 자리는 **클래스가 아니라 설정**이 되었다.
 * 시험이 보는 것은 그대로다 — **어디에 뜨는가**와 **몇 개까지 쌓이는가**.
 */
describe("고정 요소의 자리 (#134)", () => {
  test("토스트는 우측 하단이다", () => {
    // 가운데는 눈이 가장 자주 머무는 자리이고 본문 위를 덮는다.
    expect(OVERLAY.toastPosition).toBe("bottom-right");
  });

  test("셋까지만 쌓인다", () => {
    // 넷 이상 쌓이면 화면 오른쪽이 통째로 가려진다 — 알리려던 것을 알리지 못하게 된다.
    expect(OVERLAY.toastMaxVisible).toBe(3);
  });

  test("좁은 화면에서 가로를 채운다", () => {
    // 우측에 붙이면 글이 서너 줄로 접힌다. 폭은 상한으로만 준다.
    expect(OVERLAY.toastItem).toContain("w-full");
    expect(OVERLAY.toastWidth).toBe("24rem");
  });

  test("색만으로 구분하지 않는다", () => {
    // 세 톤이 **테두리·배경·글자**를 함께 바꾼다. 색각 이상이 있는 사람에게
    // 초록과 빨강은 같은 회색이다. 소리 내어 읽히는 라벨은 `ToastContext` 가 붙인다.
    for (const tone of Object.values(OVERLAY.toastTone)) {
      expect(tone).toMatch(/border-/);
      expect(tone).toMatch(/bg-/);
      expect(tone).toMatch(/text-/);
    }
  });

  test("층 토큰은 sonner 가 아니라 우리가 정한 것을 쓴다", () => {
    // 숫자를 화면마다 흩뿌리면 무엇이 무엇 위에 있어야 하는지 알 수 없게 된다.
    expect(OVERLAY.toastItem).not.toMatch(/z-\d/);
  });
});
