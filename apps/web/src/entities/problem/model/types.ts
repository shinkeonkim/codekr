// 검증 결과(ProblemVerification)는 "정답 코드를 제출해서 채점한 결과"라 제출 도메인의
// 타입을 그대로 쓴다. 같은 개념을 문제 쪽에 복제하면 두 벌이 어긋난다.
import type { SubmissionStatus, TestcaseResult, Verdict } from "@/entities/submission";

/** 문제와 그 부속(테스트케이스·초기 코드·정답 코드) 표현. */

export type ProblemCategory =
  | "ALGORITHM"
  | "DATA_STRUCTURE"
  | "SQL"
  | "NETWORK"
  | "LANGUAGE"
  | "OS"
  | "SYSTEM_DESIGN";

/** solved.ac 형식 티어. 각 티어는 5단계(가장 쉬움)에서 1단계(가장 어려움)로 나뉜다. */
export type DifficultyTier = "BRONZE" | "SILVER" | "GOLD" | "PLATINUM" | "DIAMOND" | "RUBY";

export type DifficultyStep = 1 | 2 | 3 | 4 | 5;

export type Difficulty = `${DifficultyTier}_${DifficultyStep}`;

export interface Runtime {
  id: string;
  label: string;
  monacoLanguage: string;
  template: string;
}

export interface ProblemSummary {
  id: number;
  slug: string;
  title: string;
  category: ProblemCategory;
  difficulty: Difficulty;
  /** 1(브론즈 5) ~ 30(루비 1). 숫자가 클수록 어렵다. */
  difficultyLevel: number;
  tier: DifficultyTier;
  difficultyLabel: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  published: boolean;
}

export interface ProblemExample {
  seq: number;
  input: string;
  output: string;
}

export interface ProblemDetail extends Omit<ProblemSummary, "published"> {
  description: string;
  inputDescription: string | null;
  outputDescription: string | null;
  examples: ProblemExample[];
  /** template 은 문제가 지정한 초기 코드이며, 없으면 런타임 기본값이 들어온다. */
  runtimes: Runtime[];
}

export type TestcaseVisibility = "PUBLIC" | "HIDDEN";

export interface Testcase {
  id?: number;
  seq: number;
  input: string;
  expectedOutput: string;
  visibility: TestcaseVisibility;
}

/** 문제가 언어/버전별로 제공하는 초기 코드. */
export interface ProblemTemplate {
  runtimeId: string;
  sourceCode: string;
}

/** 문제의 정답 코드. 어드민 응답에만 실린다. */
export interface ProblemSolution {
  runtimeId: string;
  sourceCode: string;
}

/** 정답 코드로 전체 테스트케이스를 검증한 결과. */
export interface ProblemVerification {
  submissionId: number;
  status: SubmissionStatus;
  verdict: Verdict | null;
  passedCount: number;
  totalCount: number;
  compileError: string | null;
  /** 검증 이후 테스트케이스나 실행 제한이 바뀌었으면 true. */
  stale: boolean;
  results: TestcaseResult[];
}

export interface AdminProblemDetail extends ProblemSummary {
  description: string;
  inputDescription: string | null;
  outputDescription: string | null;
  testcases: Testcase[];
  templates: ProblemTemplate[];
  solution: ProblemSolution | null;
  verification: ProblemVerification | null;
}
