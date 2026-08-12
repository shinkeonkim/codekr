import {
  CATEGORY_LABELS,
  TierBadge,
  acceptanceLabel,
  solverLabel,
} from "@/entities/problem";
import type { ProblemCategory, ProblemSummary } from "@/entities/problem";
import type { Column } from "@/shared/ui";

/**
 * 문제 목록의 열 (#193).
 *
 * **한 칸에 값 하나다.** 전에는 `312/980명 · 31.8%` 와 `2000ms · 256MB` 처럼 뭉쳐 있어서
 * 정답률만 훑으려 해도 앞의 분수를 매번 건너뛰어야 했다. 목록의 목적은 훑어보고 고르는
 * 것인데(#79) 세로로 비교가 되지 않았다.
 *
 * 단위는 **머리글에** 둔다. 값에서 빼면 자릿수가 맞아 눈이 걸리지 않는다.
 *
 * 좁은 화면에 남는 것은 **문제·정답률·난이도 셋**이다. 고르는 데 필요한 최소이고,
 * 넷째부터는 줄이 접혀서 오히려 못 읽는다. 제출 규모(맞은 사람)와 유형은 `sm`,
 * 실행 제한은 풀기 전까지 판단에 거의 쓰이지 않으므로 `lg`.
 */
export const PROBLEM_COLUMNS: Column<ProblemSummary>[] = [
  {
    key: "id",
    header: "번호",
    /*
      **번호가 맨 왼쪽이다** (#204). 사람이 문제를 부르는 이름이고, 목록에서 "어디쯤인지"
      를 가늠하는 유일한 단서다.

      폭을 고정한다 — 자릿수가 1자리에서 5자리까지 섞이면 제목의 시작점이 줄마다 흔들린다.
    */
    width: "w-20",
    render: (problem) => <span className="tabular-nums text-ink-muted">{problem.id}</span>,
  },
  { key: "title", header: "문제", render: (problem) => problem.title },
  {
    key: "category",
    header: "유형",
    hideBelow: "sm",
    render: (problem) => (
      <span className="text-ink-muted">{CATEGORY_LABELS[problem.category as ProblemCategory]}</span>
    ),
  },
  {
    key: "timeLimit",
    header: "시간(ms)",
    hideBelow: "lg",
    align: "center",
    render: (problem) => <Value value={problem.timeLimitMs} />,
  },
  {
    key: "memoryLimit",
    header: "메모리(MB)",
    hideBelow: "lg",
    align: "center",
    render: (problem) => <Value value={problem.memoryLimitMb} />,
  },
  {
    key: "solvers",
    header: "맞은 사람",
    hideBelow: "sm",
    align: "center",
    // 제출한 사람 수는 빼도 된다 — 정답률에 이미 들어 있다.
    render: (problem) => <Value value={solverLabel(problem.stats)} />,
  },
  {
    key: "acceptance",
    header: "정답률",
    align: "center",
    render: (problem) => <Value value={acceptanceLabel(problem.stats)} />,
  },
  {
    key: "difficulty",
    header: "난이도",
    align: "center",
    render: (problem) => <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />,
  },
];

function Value({ value }: { value: string | number }) {
  return <span className="whitespace-nowrap text-xs text-ink-muted">{value}</span>;
}
