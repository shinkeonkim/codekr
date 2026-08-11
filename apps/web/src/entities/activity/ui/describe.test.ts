import { describe, expect, test } from "bun:test";

/**
 * 툴팁 문구 규칙 (#133).
 *
 * 문구를 만드는 함수가 컴포넌트 안에 있어 여기서는 규칙만 다시 적어 검증한다.
 * 렌더링을 확인하는 것이 아니라, **문구가 바뀌면 눈에 띄게** 하려는 시험이다.
 */
function describeCell(label: string, submissions: number, solved: number): string {
  if (submissions === 0) return `${label} · 활동 없음`;
  return `${label} · 제출 ${submissions}회 · 맞힌 문제 ${solved}개`;
}

describe("활동 칸 툴팁", () => {
  test("활동이 없는 날은 숫자를 늘어놓지 않는다", () => {
    // "0회 제출, 0문제" 라고 쓰면 읽는 사람이 숫자를 두 번 확인하게 된다.
    expect(describeCell("2026-01-01", 0, 0)).toBe("2026-01-01 · 활동 없음");
  });

  test("제출 수와 맞힌 문제 수를 함께 보여준다", () => {
    // 진한 칸이 '한 문제를 20번 틀린 날' 인지 '20문제를 푼 날' 인지 구분되어야 한다.
    expect(describeCell("2026-01-02", 20, 1)).toContain("제출 20회");
    expect(describeCell("2026-01-02", 20, 1)).toContain("맞힌 문제 1개");
  });

  test("'새로 푼' 이 아니라 '맞힌' 이다", () => {
    // 어제 푼 문제를 오늘 다시 맞혀도 세어지므로 문구가 정확해야 한다.
    expect(describeCell("2026-01-03", 3, 2)).not.toContain("새로");
  });
});
