import type {
  OutputComparison,
  AdminProblemDetail,
  Difficulty,
  ProblemRuntimeLimit,
  ProblemSolution,
  ProblemTemplate,
  SqlSpec,
  Testcase,
} from "@/entities/problem";

/**
 * 문제 편집 폼의 값 (#127).
 *
 * 화면에서 떼어 둔 이유: 유형이 늘어날 때마다 폼이 커지는데, **값의 모양과 그리는 일이
 * 한 파일에 있으면 그 파일만 계속 자란다.**
 */
export interface ProblemFormValues {
  slug: string;
  title: string;
  category: string;
  /** 채점 방식 (#59). 분야(category)와 다른 축이다. */
  problemKind: string;
  /** SQL 유형일 때만 보낸다 (#60). */
  sqlSpec: SqlSpec | null;
  difficulty: Difficulty;
  description: string;
  inputDescription: string;
  outputDescription: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  /** 출력 비교 방식 (#279). 기본은 정확 일치다. */
  outputComparison: OutputComparison;
  /** 허용 오차. `FLOAT` 일 때만 쓰인다. */
  floatEpsilon: number;
  published: boolean;
  testcases: Testcase[];
  templates: ProblemTemplate[];
  runtimeLimits: ProblemRuntimeLimit[];
  solution: ProblemSolution | null;
}

export const EMPTY_TESTCASE: Testcase = { seq: 1, input: "", expectedOutput: "", visibility: "PUBLIC" };

// 행 순서 무시가 기본이다 — 문제가 정렬을 요구하지 않는데 순서를 비교하면
// 맞는 답이 틀린 것으로 나온다.
export const BLANK_SQL_SPEC: SqlSpec = { schemaSql: "", answerSql: "", ignoreRowOrder: true };

export function toFormValues(problem: AdminProblemDetail): ProblemFormValues {
  return {
    slug: problem.slug,
    title: problem.title,
    category: problem.category,
    problemKind: problem.problemKind,
    sqlSpec: problem.sqlSpec,
    difficulty: problem.difficulty,
    description: problem.description,
    inputDescription: problem.inputDescription ?? "",
    outputDescription: problem.outputDescription ?? "",
    timeLimitMs: problem.timeLimitMs,
    memoryLimitMb: problem.memoryLimitMb,
    outputComparison: problem.outputComparison ?? "EXACT",
    floatEpsilon: problem.floatEpsilon ?? 0,
    published: problem.published,
    testcases: problem.testcases,
    templates: problem.templates,
    runtimeLimits: problem.runtimeLimits ?? [],
    solution: problem.solution,
  };
}

export const BLANK_PROBLEM: ProblemFormValues = {
  slug: "",
  title: "",
  category: "ALGORITHM",
  problemKind: "JUDGE_STDIO",
  sqlSpec: null,
  difficulty: "BRONZE_5",
  description: "",
  inputDescription: "",
  outputDescription: "",
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  outputComparison: "EXACT",
  floatEpsilon: 0,
  published: false,
  testcases: [EMPTY_TESTCASE],
  templates: [],
  runtimeLimits: [],
  solution: null,
};


/** 문제 등록과 수정이 같은 폼을 쓴다 — 요청 본문 모양이 동일하기 때문이다. */
