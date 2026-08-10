import type { Runtime } from "@/entities/problem";

/** 제출과 채점 결과 표현. */

export type SubmissionStatus = "PENDING" | "JUDGING" | "COMPLETED" | "FAILED";

export type Verdict =
  | "ACCEPTED"
  | "WRONG_ANSWER"
  | "TIME_LIMIT_EXCEEDED"
  | "MEMORY_LIMIT_EXCEEDED"
  | "RUNTIME_ERROR"
  | "COMPILE_ERROR"
  | "OUTPUT_LIMIT_EXCEEDED"
  | "SYSTEM_ERROR";

export interface TestcaseResult {
  seq: number;
  verdict: Verdict;
  runtimeMs: number;
  memoryKb: number;
  stderrExcerpt: string | null;
}

export type SubmissionVisibility = "PUBLIC" | "PRIVATE" | "ACCEPTED_ONLY";

export interface SubmissionDetail {
  id: number;
  problemSlug: string;
  problemTitle: string;
  runtimeId: string;
  status: SubmissionStatus;
  verdict: Verdict | null;
  passedCount: number;
  totalCount: number;
  maxRuntimeMs: number;
  maxMemoryKb: number;
  compileError: string | null;
  visibility: SubmissionVisibility;
  /** 볼 권한이 없으면 아예 내려오지 않는다. */
  sourceCode: string | null;
  sourceVisible: boolean;
  nickname: string;
  results: TestcaseResult[];
  createdAt: string;
}

export interface SubmissionSummary {
  id: number;
  problemSlug: string;
  problemTitle: string;
  runtimeId: string;
  status: SubmissionStatus;
  verdict: Verdict | null;
  passedCount: number;
  totalCount: number;
  maxRuntimeMs: number;
  maxMemoryKb: number;
  visibility: SubmissionVisibility;
  sourceVisible: boolean;
  nickname: string;
  createdAt: string;
}

export interface RunResult {
  status: string;
  stdout: string;
  stderr: string;
  runtimeMs: number;
  memoryKb: number;
  truncated: boolean;
}

/** 일별 활동과 스트릭 (#36). */
