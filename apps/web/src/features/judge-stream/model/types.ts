import type { Verdict } from "@/entities/submission";

/** 채점 진행 이벤트 (WebSocket). */
export interface JudgeEvent {
  type: "SUBSCRIBED" | "JUDGING" | "TESTCASE" | "COMPLETED" | "ERROR";
  submissionId: number;
  seq?: number;
  verdict?: Verdict;
  runtimeMs?: number;
  memoryKb?: number;
  passedCount?: number;
  totalCount?: number;
  maxRuntimeMs?: number;
  maxMemoryKb?: number;
  compileError?: string;
  stderrExcerpt?: string;
  message?: string;
}
