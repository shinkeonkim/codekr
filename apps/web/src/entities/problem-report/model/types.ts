/** 신고 종류 (#478). 서버의 `ReportKind` 와 같다. */
export type ReportKind =
  | "MISSING_TESTCASE"
  | "MISSING_CONSTRAINT"
  | "WRONG_STATEMENT"
  | "WRONG_ANSWER"
  | "OTHER";

/** 처리 상태 (#478). `OPEN` 이 아직 아무도 안 본 것이다. */
export type ReportStatus = "OPEN" | "ACCEPTED" | "REJECTED";

export interface ProblemReport {
  id: number;
  problemId: number;
  reporterId: number;
  kind: ReportKind;
  kindLabel: string;
  body: string;
  status: ReportStatus;
  statusLabel: string;
  resolution: string | null;
  /**
   * 이 문제에 열려 있는 신고 수.
   *
   * **열 명이 같은 것을 말하면 그만큼 급하다** — 무엇부터 볼지 정하는 값이다.
   */
  openCount: number;
  createdAt: string;
}

/**
 * 화면에 보일 종류 이름.
 *
 * 서버도 `kindLabel` 을 주지만, **신고를 쓰기 전에는 받을 것이 없어서** 고르는
 * 자리에서는 화면이 알아야 한다.
 */
export const REPORT_KIND_LABELS: Record<ReportKind, string> = {
  MISSING_TESTCASE: "테스트케이스 부족",
  MISSING_CONSTRAINT: "제약 조건 누락",
  WRONG_STATEMENT: "지문 오류",
  WRONG_ANSWER: "정답이 틀림",
  OTHER: "그 밖에",
};
