import type {
  DifficultyState,
  OutputComparison,
  AdminProblemDetail,
  Difficulty,
  ProblemRuntimeLimit,
  ProblemSolution,
  ProblemTemplate,
  MongoSpec,
  QuizSpec,
  RedisSpec,
  RegexSpec,
  GitSpec,
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
  /** Redis 유형일 때만 보낸다 (#455). */
  redisSpec: RedisSpec | null;
  mongoSpec: MongoSpec | null;
  /** 퀴즈 유형일 때만 보낸다 (#650). */
  quizSpec: QuizSpec | null;
  /** 정규식 유형일 때만 보낸다 (#653). */
  regexSpec: RegexSpec | null;
  /** Git 유형일 때만 보낸다 (#654). */
  gitSpec: GitSpec | null;
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

export const EMPTY_TESTCASE: Testcase = {
  seq: 1,
  input: "",
  expectedOutput: "",
  visibility: "PUBLIC",
};

// 행 순서 무시가 기본이다 — 문제가 정렬을 요구하지 않는데 순서를 비교하면
// 맞는 답이 틀린 것으로 나온다.
export const BLANK_REDIS_SPEC: RedisSpec = {
  seedCommands: null,
  answerCommands: "",
  verifyCommands: "",
  // 기본은 순서를 지킨다 (#455) — 정렬 집합·리스트에서 순서는 자료의 일부다.
  ignoreOrder: false,
};

/** MongoDB 도 기본은 순서를 지킨다 (#527) — Redis 와 같은 판단이다. */
export const BLANK_MONGO_SPEC: MongoSpec = {
  seedScript: null,
  answerScript: "",
  verifyScript: "",
  ignoreOrder: false,
};

/** 새 퀴즈의 기본값 (#650). 보기 둘로 시작한다 — 하나뿐이면 고를 것이 없다. */
export const BLANK_QUIZ_SPEC: QuizSpec = {
  answerType: "SINGLE",
  explanation: null,
  choices: [
    { content: "", correct: false },
    { content: "", correct: false },
  ],
  answers: [],
  ignoreCase: true,
  ignoreWhitespace: true,
};

/**
 * 새 정규식 문제의 기본값 (#653).
 *
 * **맞으면 안 되는 줄을 미리 넣어 둔다.** 없으면 `.*` 가 통과하는 문제가 되고,
 * 서버가 막기는 하지만 **왜 막히는지**를 이 자리에서 먼저 보여 주는 편이 낫다.
 */
export const BLANK_REGEX_SPEC: RegexSpec = {
  cases: "+맞아야 하는 문자열\n-맞으면 안 되는 문자열\n",
  fullMatch: true,
  ignoreCase: false,
};

/**
 * 새 Git 문제의 기본값 (#654).
 *
 * 확인 명령을 **트리 해시로 미리 채워 둔다.** 커밋 해시(`%H`)로 두면 메시지 한 글자만
 * 달라도 같은 결과에 이른 다른 풀이가 틀린 답이 된다 — 그것을 기본으로 만들지 않는다.
 */
export const BLANK_GIT_SPEC: GitSpec = {
  seedCommands: "git commit -q --allow-empty -m base\n",
  answerCommands: "",
  verifyCommands: "git log --format='%T %s'\n",
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
    redisSpec: problem.redisSpec ?? null,
    mongoSpec: problem.mongoSpec ?? null,
    quizSpec: problem.quizSpec ?? null,
    regexSpec: problem.regexSpec ?? null,
    gitSpec: problem.gitSpec ?? null,
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
    setters: (problem.setters ?? []).map((each) => ({
      id: each.userId,
      nickname: each.nickname,
    })),
    reviewers: (problem.reviewers ?? []).map((each) => ({
      id: each.userId,
      nickname: each.nickname,
    })),
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
  redisSpec: null,
  mongoSpec: null,
  quizSpec: null,
  regexSpec: null,
  gitSpec: null,
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
  // 하네스를 쓰는 유형 둘 (#421, #651). 허용 언어를 정하는 규칙이 같다.
  const usesHarness =
    values.problemKind === "JUDGE_FUNCTION" || values.problemKind === "JUDGE_PATCH";
  return {
    ...rest,
    /*
      **하네스를 쓰는 유형은 하네스가 곧 허용 언어다** (#446, #651). 둘을 함께 보내면 서버가 거부한다 —
      화면이 실수로 남겨 둔 값 때문에 저장이 막히지 않게 여기서 정리한다.
    */
    allowedRuntimeIds: usesHarness ? [] : values.allowedRuntimeIds,
    harnesses: usesHarness ? values.harnesses : {},
    setterIds: setters.map((each) => each.id),
    reviewerIds: reviewers.map((each) => each.id),
    // 빈 칸은 보내지 않는다 — 빈 문자열을 저장하면 "없음" 과 "빈 값" 이 갈린다.
    sourceLabel: sourceLabel.trim() || null,
    sourceUrl: sourceUrl.trim() || null,
  };
}
