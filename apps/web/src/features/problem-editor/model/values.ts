import type {
  DifficultyState,
  OutputComparison,
  AdminProblemDetail,
  Difficulty,
  ProblemRuntimeLimit,
  ProblemSolution,
  ProblemTemplate,
  NoSqlSpec,
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
  /** NoSQL 유형일 때만 보낸다 (#455). */
  nosqlSpec: NoSqlSpec | null;
  /** 비워 둘 수 있다 (#195) — 그때 `difficultyState` 가 뜻을 갖는다. */
  difficulty: Difficulty | null;
  difficultyState: DifficultyState;
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
  /**
   * 출제자·검수자 (#236). **닉네임으로 찾아 고른다** — id 를 손으로 치게 하지 않는다.
   */
  setters: { id: number; nickname: string }[];
  reviewers: { id: number; nickname: string }[];
  /** 출처 (#236). 선택이다 — 자체 제작이면 비운다. */
  sourceLabel: string;
  sourceUrl: string;
  testcases: Testcase[];
  templates: ProblemTemplate[];
  runtimeLimits: ProblemRuntimeLimit[];
  /** 풀 수 있는 언어 (#419). **비우면 이 유형의 전부**를 허용한다. */
  allowedRuntimeIds: string[];
  /** 언어별 하네스 (#448). **여기 쓴 언어가 곧 풀 수 있는 언어다.** */
  harnesses: Record<string, string>;
  solution: ProblemSolution | null;
}

export const EMPTY_TESTCASE: Testcase = { seq: 1, input: "", expectedOutput: "", visibility: "PUBLIC" };

// 행 순서 무시가 기본이다 — 문제가 정렬을 요구하지 않는데 순서를 비교하면
// 맞는 답이 틀린 것으로 나온다.
export const BLANK_NOSQL_SPEC: NoSqlSpec = {
  seedCommands: null,
  answerCommands: "",
  verifyCommands: "",
  // 기본은 순서를 지킨다 (#455) — 정렬 집합·리스트에서 순서는 자료의 일부다.
  ignoreOrder: false,
};

export const BLANK_SQL_SPEC: SqlSpec = {
  schemaSql: "",
  answerSql: "",
  ignoreRowOrder: true,
  // 기본은 읽기 전용이다 (#453). 여는 것은 출제자가 그 문제에서 정한다.
  verifySql: null,
  allowWrite: false,
};

export function toFormValues(problem: AdminProblemDetail): ProblemFormValues {
  return {
    slug: problem.slug,
    title: problem.title,
    category: problem.category,
    problemKind: problem.problemKind,
    sqlSpec: problem.sqlSpec,
    nosqlSpec: problem.nosqlSpec ?? null,
    difficulty: problem.difficulty,
    difficultyState: problem.difficultyState ?? "UNRATED",
    description: problem.description,
    inputDescription: problem.inputDescription ?? "",
    outputDescription: problem.outputDescription ?? "",
    timeLimitMs: problem.timeLimitMs,
    memoryLimitMb: problem.memoryLimitMb,
    outputComparison: problem.outputComparison ?? "EXACT",
    floatEpsilon: problem.floatEpsilon ?? 0,
    published: problem.published,
    setters: (problem.setters ?? []).map((each) => ({ id: each.userId, nickname: each.nickname })),
    reviewers: (problem.reviewers ?? []).map((each) => ({ id: each.userId, nickname: each.nickname })),
    sourceLabel: problem.sourceLabel ?? "",
    sourceUrl: problem.sourceUrl ?? "",
    testcases: problem.testcases,
    templates: problem.templates,
    runtimeLimits: problem.runtimeLimits ?? [],
    allowedRuntimeIds: problem.allowedRuntimeIds ?? [],
    harnesses: problem.harnesses ?? {},
    solution: problem.solution,
  };
}

export const BLANK_PROBLEM: ProblemFormValues = {
  slug: "",
  title: "",
  category: "ALGORITHM",
  problemKind: "JUDGE_STDIO",
  sqlSpec: null,
  nosqlSpec: null,
  // 새 문제의 기본은 **미평가**다 (#195). 브론즈로 두면 "아직 안 정했다" 와
  // "쉽다" 가 구분되지 않는다 — 등록은 쉬워지지만 거짓 정보가 섞인다.
  difficulty: null,
  difficultyState: "UNRATED",
  description: "",
  inputDescription: "",
  outputDescription: "",
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  outputComparison: "EXACT",
  floatEpsilon: 0,
  published: false,
  setters: [],
  reviewers: [],
  sourceLabel: "",
  sourceUrl: "",
  testcases: [EMPTY_TESTCASE],
  templates: [],
  runtimeLimits: [],
  allowedRuntimeIds: [],
  harnesses: {},
  solution: null,
};


/** 문제 등록과 수정이 같은 폼을 쓴다 — 요청 본문 모양이 동일하기 때문이다. */

/**
 * 폼 값 → 저장 요청 (#236).
 *
 * **사람은 이름으로 고르고 서버는 id 를 받는다.** 그 변환을 화면마다 하면 한 곳이
 * 빠졌을 때 출제자가 조용히 사라진다 — 등록과 수정이 같은 함수를 쓴다.
 */
export function toRequest(values: ProblemFormValues) {
  const { setters, reviewers, sourceLabel, sourceUrl, ...rest } = values;
  const functionKind = values.problemKind === "JUDGE_FUNCTION";
  return {
    ...rest,
    /*
      **함수형은 하네스가 곧 허용 언어다** (#446). 둘을 함께 보내면 서버가 거부한다 —
      화면이 실수로 남겨 둔 값 때문에 저장이 막히지 않게 여기서 정리한다.
    */
    allowedRuntimeIds: functionKind ? [] : values.allowedRuntimeIds,
    harnesses: functionKind ? values.harnesses : {},
    setterIds: setters.map((each) => each.id),
    reviewerIds: reviewers.map((each) => each.id),
    // 빈 칸은 보내지 않는다 — 빈 문자열을 저장하면 "없음" 과 "빈 값" 이 갈린다.
    sourceLabel: sourceLabel.trim() || null,
    sourceUrl: sourceUrl.trim() || null,
  };
}
