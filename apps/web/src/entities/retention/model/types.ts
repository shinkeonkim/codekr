/**
 * 정리 배치가 지운 것 (#46, #552).
 *
 * `truncated` 면 상한에 걸려 남은 것이 있다는 뜻이다 — 다음 실행이 이어서 지운다.
 * 그 사실을 말해 주지 않으면 "한 번 눌렀으니 다 지워졌다" 고 믿게 된다.
 */
export interface RetentionReport {
  executedAt: string;
  deletedProblems: number;
  deletedTestcases: number;
  deletedTemplates: number;
  deletedNotifications: number;
  truncated: boolean;
  total: number;
}
