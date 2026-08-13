import type { ProblemTag } from "@/entities/tag";

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

/**
 * 난이도가 매겨진 상태인가 (#195).
 *
 * `UNRATED` 는 "아직 모른다"(곧 값이 붙는다), `NO_RATE` 는 "해당 없다"(영영 없다).
 * **둘 다 문제 수에는 세고 점수에는 넣지 않는다.**
 */
export type DifficultyState = "RATED" | "UNRATED" | "NO_RATE";

export interface Runtime {
  id: string;
  label: string;
  monacoLanguage: string;
  template: string;
  /** 이 런타임으로 실행할 때 실제 적용되는 제한. 문제 기본값과 다를 수 있다 (#97). */
  timeLimitMs: number;
  memoryLimitMb: number;
  /** 문제 기본값이 아니라 이 런타임 전용 값이 쓰였는지. */
  limitOverridden: boolean;
}

/** 문제 풀이 통계 (#84). 제출 수가 아니라 **사람 수**로 센다. */
export interface ProblemStats {
  submitterCount: number;
  solverCount: number;
  /** 맞은 사람 / 제출한 사람. 아무도 제출하지 않았으면 null. */
  acceptanceRate: number | null;
}

export interface ProblemSummary {
  id: number;
  slug: string;
  title: string;
  category: ProblemCategory;
  /** 미평가·평가안함이면 `null` 이다 (#195). */
  difficulty: Difficulty | null;
  difficultyState: DifficultyState;
  /** 1(브론즈 5) ~ 30(루비 1). 숫자가 클수록 어렵다. */
  difficultyLevel: number | null;
  tier: DifficultyTier | null;
  difficultyLabel: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  published: boolean;
  stats: ProblemStats;
}

export interface ProblemExample {
  seq: number;
  input: string;
  output: string;
}

export interface ProblemDetail extends Omit<ProblemSummary, "published"> {
  /** 누가 만들고 검수했는가 (#236). 탈퇴한 사람은 "탈퇴한 사용자" 로 온다 (#140). */
  setters: ProblemCredit[];
  reviewers: ProblemCredit[];
  /** 어디서 온 문제인가 (#236). 자체 제작이면 비어 있다. */
  sourceLabel: string | null;
  sourceUrl: string | null;
  description: string;
  inputDescription: string | null;
  outputDescription: string | null;
  examples: ProblemExample[];
  /** template 은 문제가 지정한 초기 코드이며, 없으면 런타임 기본값이 들어온다. */
  runtimes: Runtime[];
  /** 알고리즘 분류 (#232). 화면에서 기본으로 접어 둔다 — 풀이 힌트가 된다. */
  tags: ProblemTag[];
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
/** 런타임별 실행 제한 오버라이드 (어드민 편집용). */
export interface ProblemRuntimeLimit {
  runtimeId: string;
  timeLimitMs: number;
  memoryLimitMb: number;
}

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

/**
 * 채점 방식 (#59).
 *
 * **분야(`ProblemCategory`)와는 다른 축이다** — 분야는 무엇에 대한 문제인지를,
 * 채점 방식은 어떻게 채점하는지를 말한다. 화면에서도 두 말을 구분해 쓴다.
 */
export type ProblemKind = "JUDGE_STDIO" | "JUDGE_SQL" | "QUIZ" | "MANUAL";

/**
 * 문제 목록 정렬 (#132). 서버의 `ProblemSort` 와 같은 값이다.
 *
 * **방향은 기준마다 하나로 고정한다.** 오름/내림을 열면 값이 두 배가 되는데,
 * "제목 내림차순" 처럼 아무도 쓰지 않는 조합이 절반이다.
 */
export type ProblemSort =
  | "LATEST"
  | "OLDEST"
  | "TITLE"
  | "DIFFICULTY"
  | "DIFFICULTY_DESC"
  | "SOLVERS_DESC"
  | "SOLVERS_ASC"
  | "ACCEPTANCE_DESC"
  | "ACCEPTANCE_ASC";

/**
 * 정렬 선택지 (#132, #205).
 *
 * **Select 하나에 다 넣는다.** 기준과 방향을 두 칸으로 나누면 "제목순 내림차순" 같은
 * 뜻 없는 조합이 생기고, 주소에 남는 상태도 둘이 된다 (#132 의 규칙).
 *
 * 이름을 방향까지 말하도록 적는다 — `많이 풀린 순` 이 `맞은 사람 내림차순` 보다 짧고
 * 무엇을 찾는지에 가깝다.
 */
export const PROBLEM_SORTS: { value: ProblemSort; label: string }[] = [
  { value: "LATEST", label: "최신순" },
  { value: "OLDEST", label: "오래된순" },
  { value: "DIFFICULTY", label: "쉬운순" },
  { value: "DIFFICULTY_DESC", label: "어려운순" },
  { value: "SOLVERS_DESC", label: "많이 풀린 순" },
  { value: "SOLVERS_ASC", label: "적게 풀린 순" },
  { value: "ACCEPTANCE_DESC", label: "정답률 높은 순" },
  { value: "ACCEPTANCE_ASC", label: "정답률 낮은 순" },
  { value: "TITLE", label: "제목순" },
];

/** 지금 고를 수 있는 채점 방식. 나머지는 채점기 구현이 없어 서버가 거절한다. */
export const SELECTABLE_KINDS: Record<string, string> = {
  JUDGE_STDIO: "코드 실행 (stdin/stdout)",
  JUDGE_SQL: "SQL",
};

/**
 * SQL 문제의 스펙 (#60).
 *
 * **정답은 결과 집합이 아니라 쿼리다.** 시드 데이터가 바뀌면 기대 결과도 따라간다.
 */
export interface SqlSpec {
  schemaSql: string;
  answerSql: string;
  /** 문제가 정렬을 요구하지 않으면 켜 둔다. 켠 채로 두는 것이 기본이다. */
  ignoreRowOrder: boolean;
}

/** 출력 비교 방식 (#279). */
export type OutputComparison = "EXACT" | "FLOAT";

export const OUTPUT_COMPARISON_LABELS: Record<OutputComparison, string> = {
  EXACT: "정확 일치",
  FLOAT: "실수 오차 허용",
};

/** 문제에 이름이 붙은 사람 하나 (#236). */
export interface ProblemCredit {
  userId: number;
  nickname: string;
  role: "SETTER" | "REVIEWER";
  roleLabel: string;
}

export interface AdminProblemDetail extends ProblemSummary {
  setters?: ProblemCredit[];
  reviewers?: ProblemCredit[];
  sourceLabel?: string | null;
  sourceUrl?: string | null;
  outputComparison: OutputComparison;
  floatEpsilon: number;
  problemKind: ProblemKind;
  /** SQL 유형이 아니면 null (#60). */
  sqlSpec: SqlSpec | null;
  description: string;
  inputDescription: string | null;
  outputDescription: string | null;
  testcases: Testcase[];
  templates: ProblemTemplate[];
  runtimeLimits: ProblemRuntimeLimit[];
  solution: ProblemSolution | null;
  verification: ProblemVerification | null;
  /** 지금 붙어 있는 알고리즘 분류 (#232). */
  tags: ProblemTag[];
}
