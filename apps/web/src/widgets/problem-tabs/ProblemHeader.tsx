import { CATEGORY_LABELS } from "@/entities/problem";
import type { ProblemDetail, Runtime } from "@/entities/problem";
import { TierBadge } from "@/entities/problem";
import { Badge } from "@/shared/ui";

interface Props {
  problem: ProblemDetail;
  /**
   * 지금 고른 실행 환경. 주면 그 언어에 적용되는 제한을 보여준다 (#97).
   *
   * 언어별 제한이 있는 문제에서 문제 기본값만 보여주면, 실제 채점과 다른 숫자를 보고
   * 코드를 짜게 된다.
   */
  runtime?: Runtime;
}

/** 세 화면이 공유하는 문제 머리말. 어느 탭에 있어도 무슨 문제인지 알 수 있어야 한다. */
export function ProblemHeader({ problem, runtime }: Props) {
  const timeLimitMs = runtime?.timeLimitMs ?? problem.timeLimitMs;
  const memoryLimitMb = runtime?.memoryLimitMb ?? problem.memoryLimitMb;

  return (
    <header className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="info">{CATEGORY_LABELS[problem.category]}</Badge>
        <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
        <span className="text-xs text-ink-muted">
          시간 {timeLimitMs}ms · 메모리 {memoryLimitMb}MB
        </span>
        {runtime?.limitOverridden ? (
          <Badge tone="warn">{runtime.label} 전용 제한</Badge>
        ) : null}
      </div>
      {/*
        `1000번: A + B` — 화면마다 다르게 적지 않는다 (#204). 표에서는 번호가 제 열을
        갖고, 제목과 나란히 쓸 때는 이 형태다.
      */}
      <h1 className="text-2xl font-bold text-ink">
        <span className="tabular-nums text-ink-muted">{problem.id}번</span>
        <span className="text-ink-muted">: </span>
        {problem.title}
      </h1>
    </header>
  );
}
