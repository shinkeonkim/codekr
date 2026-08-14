import type { ProblemImportPreview } from "@/entities/problem";
import { describe, expect, it } from "bun:test";
import { importWarnings, isBlocked } from "./warnings";

function preview(overrides: Partial<ProblemImportPreview> = {}): ProblemImportPreview {
  return {
    source: "JSON",
    slug: "a-plus-b",
    title: "A + B",
    category: "ALGORITHM",
    problemKind: "JUDGE_STDIO",
    difficulty: "BRONZE_5",
    timeLimitMs: 2000,
    memoryLimitMb: 256,
    testcaseCount: 6,
    needsTestcases: true,
    testcaseSource: "INLINE",
    templateCount: 1,
    publishedInBundle: false,
    violations: [],
    ...overrides,
  };
}

describe("importWarnings", () => {
  it("멀쩡한 묶음에는 할 말이 없다", () => {
    expect(importWarnings(preview())).toEqual([]);
    expect(isBlocked(preview())).toBe(false);
  });

  it("테스트케이스가 없으면 알리되 막지는 않는다", () => {
    // 테스트케이스를 나중에 붙이는 순서도 있다.
    const warnings = importWarnings(preview({ testcaseCount: 0 }));

    expect(warnings).toHaveLength(1);
    expect(warnings[0].blocking).toBe(false);
    expect(isBlocked(preview({ testcaseCount: 0 }))).toBe(false);
  });

  it("published 가 적혀 있어도 초안으로 들어간다고 알린다", () => {
    // 말해 주지 않으면 "왜 공개가 안 됐지" 를 겪는다.
    const warnings = importWarnings(preview({ publishedInBundle: true }));

    expect(warnings).toHaveLength(1);
    expect(warnings[0].blocking).toBe(false);
    expect(warnings[0].message).toContain("초안");
  });

  it("SQL 처럼 테스트케이스가 필요 없는 유형은 0개여도 말이 없다", () => {
    // SQL 은 정답 쿼리로 채점한다 — 0개가 정상이다 (#561).
    const sql = preview({ problemKind: "JUDGE_SQL", needsTestcases: false, testcaseCount: 0 });

    expect(importWarnings(sql)).toEqual([]);
    expect(isBlocked(sql)).toBe(false);
  });

  it("검증 위반은 저장을 막는다", () => {
    const one = preview({ violations: ["title: 비어 있을 수 없습니다"] });

    expect(importWarnings(one)[0].blocking).toBe(true);
    expect(isBlocked(one)).toBe(true);
  });

  it("위반을 하나만 보이고 멈추지 않는다", () => {
    // 첫 번째에서 멈추면 고치고 다시 올리기를 반복하게 된다.
    const many = preview({
      violations: ["title: 비어 있을 수 없습니다", "slug: 형식이 맞지 않습니다"],
    });

    expect(importWarnings(many).filter((it) => it.blocking)).toHaveLength(2);
  });

  it("막는 것과 알리는 것이 함께 나온다", () => {
    const both = preview({ testcaseCount: 0, publishedInBundle: true, violations: ["slug: x"] });
    const warnings = importWarnings(both);

    expect(warnings).toHaveLength(3);
    expect(warnings.filter((it) => it.blocking)).toHaveLength(1);
    expect(isBlocked(both)).toBe(true);
  });
});
