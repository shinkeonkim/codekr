/** API 응답 타입. docs/03_API_명세.md 와 짝을 이룬다. */

export type UserRole = "USER" | "ADMIN";

export interface User {
  id: number;
  email: string;
  nickname: string;
  role: UserRole;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

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
export interface DailyActivity {
  date: string;
  count: number;
}

export interface ActivityResponse {
  from: string;
  to: string;
  days: DailyActivity[];
  totalCount: number;
  activeDayCount: number;
  currentStreak: number;
  longestStreak: number;
  timeZone: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StreamStatus {
  name: string;
  group: string;
  length: number;
  pending: number;
  consumers: number;
  lastDeliveredId: string | null;
  ready: boolean;
}

export interface QueueStatus {
  streams: StreamStatus[];
}

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
