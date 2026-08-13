import type { SubmissionStatus, SubmissionVisibility, Verdict } from "./types";

export const VERDICT_LABELS: Record<Verdict, string> = {
  ACCEPTED: "정답",
  WRONG_ANSWER: "오답",
  TIME_LIMIT_EXCEEDED: "시간 초과",
  MEMORY_LIMIT_EXCEEDED: "메모리 초과",
  RUNTIME_ERROR: "런타임 에러",
  COMPILE_ERROR: "컴파일 에러",
  OUTPUT_LIMIT_EXCEEDED: "출력 초과",
  SYSTEM_ERROR: "채점 오류",
};

export const VISIBILITY_LABELS: Record<SubmissionVisibility, string> = {
  PUBLIC: "전체 공개",
  PRIVATE: "비공개",
  ACCEPTED_ONLY: "정답일 때만 공개",
};

/** 공개 옵션을 고를 때 각 선택이 무엇을 뜻하는지 함께 보여준다. */
export const VISIBILITY_DESCRIPTIONS: Record<SubmissionVisibility, string> = {
  PUBLIC: "판정과 관계없이 다른 회원이 코드를 볼 수 있습니다.",
  PRIVATE: "나와 관리자만 코드를 볼 수 있습니다.",
  ACCEPTED_ONLY: "정답으로 확정된 뒤에만 코드가 공개됩니다.",
};

/**
 * 남의 코드를 볼 수 없을 때 그 자리에 적는 말 (#385).
 *
 * **한 곳에서만 만든다.** 전에는 세 곳이 다르게 말했다 — "작성자가 코드를 공개하지
 * 않았습니다", "작성자가 공개하지 않은 코드입니다.", 그리고 목록의 "비공개".
 * 읽는 사람에게는 **서로 다른 상태**로 보인다. #140 이 탈퇴 표시에서 같은 판단을 했다.
 *
 * **"작성자가" 를 빼는 이유**: 누가 정했는지는 보는 사람이 할 수 있는 일을 바꾸지
 * 않는다. `ACCEPTED_ONLY` 로 아직 안 열린 코드도 같은 자리에 오는데, 그때 "작성자가
 * 공개하지 않았다" 는 사실과도 어긋난다 — 정답이 되면 열린다.
 */
export const SOURCE_HIDDEN = "코드가 공개되지 않습니다";

export const STATUS_LABELS: Record<SubmissionStatus, string> = {
  PENDING: "대기 중",
  JUDGING: "채점 중",
  COMPLETED: "완료",
  FAILED: "실패",
};

/** 판정에 대응하는 시맨틱 색. 정답만 초록, 나머지는 실패 계열로 구분한다. */
export function verdictTone(verdict: Verdict | null | undefined): "ok" | "danger" | "warn" | "muted" {
  if (!verdict) return "muted";
  if (verdict === "ACCEPTED") return "ok";
  if (verdict === "SYSTEM_ERROR") return "warn";
  return "danger";
}
