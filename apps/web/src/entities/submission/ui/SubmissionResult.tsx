import { Badge } from "@/shared/ui";
import { STATUS_LABELS, VERDICT_LABELS, verdictTone } from "../model/labels";
import type { SubmissionSummary } from "../model/types";

/**
 * 제출 한 건의 결과 표시.
 *
 * **채점이 끝나지 않은 제출을 판정과 뭉뚱그리면 고장처럼 읽힌다** (#78).
 * 큐가 밀려 기다리는 것은 정상 동작이므로, 대기 중·채점 중을 눈에 띄게 구분한다.
 * 그러지 않으면 사용자가 새로고침하거나 다시 제출하고, 그러면 큐가 더 밀린다.
 */
export function SubmissionResult({ submission }: { submission: SubmissionSummary }) {
  if (submission.status === "PENDING" || submission.status === "JUDGING") {
    return (
      <span className="inline-flex items-center gap-1.5">
        <span className="size-1.5 animate-pulse rounded-full bg-info" aria-hidden />
        <span className="whitespace-nowrap text-xs text-info">{STATUS_LABELS[submission.status]}</span>
      </span>
    );
  }

  if (!submission.verdict) return <Badge>{STATUS_LABELS[submission.status]}</Badge>;

  return (
    <span className="inline-flex items-center gap-2">
      <Badge tone={verdictTone(submission.verdict)}>
        {VERDICT_LABELS[submission.verdict]} · {submission.passedCount}/{submission.totalCount}
      </Badge>
      {submission.sourceVisible ? null : (
        <span
          className="whitespace-nowrap text-xs text-ink-muted"
          title="작성자가 코드를 공개하지 않았습니다"
        >
          비공개
        </span>
      )}
    </span>
  );
}
