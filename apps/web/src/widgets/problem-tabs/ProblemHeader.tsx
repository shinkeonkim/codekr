import { CATEGORY_LABELS } from "@/entities/problem";
import type { ProblemDetail } from "@/entities/problem";
import { TierBadge } from "@/entities/problem";
import { Badge } from "@/shared/ui";

/** 세 화면이 공유하는 문제 머리말. 어느 탭에 있어도 무슨 문제인지 알 수 있어야 한다. */
export function ProblemHeader({ problem }: { problem: ProblemDetail }) {
  return (
    <header className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="info">{CATEGORY_LABELS[problem.category]}</Badge>
        <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
        <span className="text-xs text-ink-muted">
          시간 {problem.timeLimitMs}ms · 메모리 {problem.memoryLimitMb}MB
        </span>
      </div>
      <h1 className="text-2xl font-bold text-ink">{problem.title}</h1>
    </header>
  );
}
