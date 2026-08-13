import { describe, expect, test } from "bun:test";
import { SOURCE_HIDDEN, VISIBILITY_DESCRIPTIONS, VISIBILITY_LABELS } from "./labels";

/**
 * 공개 범위 문구 (#385).
 *
 * **한 곳에서만 만든다.** 세 곳이 다르게 말하면 읽는 사람에게는 서로 다른 상태로
 * 보인다 — #140 이 탈퇴 표시에서 같은 판단을 했다.
 */
describe("공개 범위", () => {
  test("세 범위가 모두 이름과 설명을 가진다", () => {
    // 하나만 고치면 셋 중 하나만 다듬어진 상태가 된다.
    for (const key of Object.keys(VISIBILITY_LABELS)) {
      expect(VISIBILITY_LABELS[key as keyof typeof VISIBILITY_LABELS]).toBeTruthy();
      expect(VISIBILITY_DESCRIPTIONS[key as keyof typeof VISIBILITY_DESCRIPTIONS]).toBeTruthy();
    }
    expect(Object.keys(VISIBILITY_DESCRIPTIONS)).toEqual(Object.keys(VISIBILITY_LABELS));
  });

  test("못 보는 코드 자리의 문구는 하나다", () => {
    expect(SOURCE_HIDDEN).toBe("코드가 공개되지 않습니다");
  });

  test("**누가 정했는지를 말하지 않는다**", () => {
    /*
      "작성자가 공개하지 않았다" 는 `ACCEPTED_ONLY` 로 아직 안 열린 코드에는 사실과
      어긋난다 — 정답이 되면 열린다. 그리고 누가 정했는지는 보는 사람이 할 수 있는
      일을 바꾸지 않는다.
    */
    expect(SOURCE_HIDDEN).not.toContain("작성자");
  });
});
