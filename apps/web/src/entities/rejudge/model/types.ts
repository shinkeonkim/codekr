/** 재채점 배치 하나 (#107, #187). */
export interface RejudgeBatch {
  id: number;
  problemId: number;
  reason: string;
  targetCount: number;
  /** 결과가 돌아온 제출 수. `targetCount` 에 닿으면 끝난 것이다. */
  processedCount: number;
  changedCount: number;
  finished: boolean;
  createdAt: string;
}

/**
 * 누르기 전에 알아야 할 것 (#219).
 *
 * `targetCount` 는 **지금 누르면** 다시 채점될 제출 수다. 알림을 받게 될 사람의 규모이므로
 * 확인 문구에 그대로 들어간다.
 */
export interface RejudgeStatus {
  problemId: number;
  targetCount: number;
  latest: RejudgeBatch | null;
}
