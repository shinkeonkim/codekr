import { describe, expect, it } from "bun:test";
import { guestGate } from "./guestGate";

describe("guestGate", () => {
  it("판정 전에는 아무것도 그리지 않는다", () => {
    // 여기서 "allow" 를 돌려주면 로그인한 사람에게 폼이 번쩍인다 (#311).
    expect(guestGate(true, false)).toBe("wait");
    expect(guestGate(true, true)).toBe("wait");
  });

  it("로그인했으면 내보낸다", () => {
    expect(guestGate(false, true)).toBe("redirect");
  });

  it("비로그인에게는 아무것도 달라지지 않는다", () => {
    expect(guestGate(false, false)).toBe("allow");
  });
});
