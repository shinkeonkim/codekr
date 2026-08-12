import type { ProblemStats } from "../model/types";

/** 정답률 표시. 아직 아무도 제출하지 않았으면 0% 가 아니라 "-" 다 (#84). */
export function acceptanceLabel(stats: ProblemStats): string {
  if (stats.acceptanceRate === null) return "-";
  return `${Math.round(stats.acceptanceRate * 100)}%`;
}

/**
 * 맞은 사람 수 표시.
 *
 * 아무도 제출하지 않았으면 `0` 이 아니라 `-` 다 — 정답률과 같은 규칙이고(#84), 목록에서
 * "아무도 안 풀었다" 와 "다들 틀렸다" 가 같은 값으로 보이면 안 된다.
 *
 * "아직 없음" 같은 문장 대신 `-` 인 이유: 열로 나뉜 뒤에는(#193) 이 칸이 세로로 비교되는데,
 * 한 줄만 길어지면 축이 흔들린다.
 */
export function solverLabel(stats: ProblemStats): string {
  if (stats.submitterCount === 0) return "-";
  return stats.solverCount.toLocaleString("ko-KR");
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
