import { Badge } from "@/shared/ui";
import type { ContestSummary } from "../model/types";

/**
 * 대회 단계 표시 (#61).
 *
 * **서버가 판정한 값을 그대로 쓴다.** 화면이 시작·종료 시각을 보고 스스로 정하면,
 * 사용자 시계가 틀린 만큼 대회가 일찍 또는 늦게 시작한 것으로 보인다.
 */
export function PhaseBadge({ contest }: { contest: ContestSummary }) {
  const tone =
    contest.phase === "RUNNING" ? "ok" : contest.phase === "SCHEDULED" ? "info" : "muted";

  return (
    <span className="flex items-center gap-1.5">
      <Badge tone={tone}>{contest.phaseLabel}</Badge>
      {contest.frozen ? <Badge tone="muted">🔒 순위 동결</Badge> : null}
    </span>
  );
}
