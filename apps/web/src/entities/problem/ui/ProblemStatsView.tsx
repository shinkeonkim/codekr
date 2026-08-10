import type { ProblemStats } from "../model/types";

/** 정답률 표시. 아직 아무도 제출하지 않았으면 0% 가 아니라 "-" 다 (#84). */
export function acceptanceLabel(stats: ProblemStats): string {
  if (stats.acceptanceRate === null) return "-";
  return `${Math.round(stats.acceptanceRate * 100)}%`;
}

/** 목록 한 칸에 들어가는 압축 표시. */
export function ProblemStatsCell({ stats }: { stats: ProblemStats }) {
  if (stats.submitterCount === 0) {
    return <span className="whitespace-nowrap text-xs text-ink-muted">아직 없음</span>;
  }
  return (
    <span className="whitespace-nowrap text-xs text-ink-muted">
      {stats.solverCount}/{stats.submitterCount}명 · {acceptanceLabel(stats)}
    </span>
  );
}

/** 문제 상세의 통계 묶음. */
export function ProblemStatsSummary({ stats }: { stats: ProblemStats }) {
  return (
    <dl className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-ink-muted">
      <div className="flex gap-1.5">
        <dt>제출한 사람</dt>
        <dd className="font-medium text-ink">{stats.submitterCount.toLocaleString("ko-KR")}</dd>
      </div>
      <div className="flex gap-1.5">
        <dt>맞은 사람</dt>
        <dd className="font-medium text-ink">{stats.solverCount.toLocaleString("ko-KR")}</dd>
      </div>
      <div className="flex gap-1.5">
        <dt>정답률</dt>
        <dd className="font-medium text-ink">{acceptanceLabel(stats)}</dd>
      </div>
    </dl>
  );
}
