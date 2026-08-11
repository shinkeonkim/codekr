import type { RejudgeStatus } from "@/entities/rejudge";

/**
 * 지금 이 문제가 어떤 상태인지 (#219).
 *
 * **누르기 전에 보여준다.** 재채점은 대상이었던 회원 모두에게 알림을 보내고(#187),
 * 그것은 되돌릴 수 없다. 몇 명에게 갈지 모른 채 누르게 하면 안 된다.
 */
export function RejudgeStatusView({ status }: { status: RejudgeStatus }) {
  const latest = status.latest;
  const running = latest !== null && !latest.finished;

  return (
    <div className="space-y-2 rounded-lg border border-border bg-surface-muted/40 p-3 text-xs">
      <p className="text-ink">
        지금 누르면 <strong className="font-medium">{status.targetCount.toLocaleString("ko-KR")}건</strong>
        을 다시 채점합니다.
        {status.targetCount === 0 ? " 대상이 없어 실행할 수 없습니다." : ""}
      </p>

      {latest ? (
        <p className={running ? "text-warn" : "text-ink-muted"}>
          {running
            ? `재채점이 진행 중입니다 — ${latest.processedCount}/${latest.targetCount}건 처리, 판정 변경 ${latest.changedCount}건.`
            : `마지막 재채점: ${latest.targetCount}건 중 ${latest.changedCount}건의 판정이 바뀌었습니다.`}
          {` (${new Date(latest.createdAt).toLocaleString("ko-KR")} · "${latest.reason}")`}
        </p>
      ) : (
        <p className="text-ink-muted">이 문제를 재채점한 적이 없습니다.</p>
      )}

      {running ? (
        <p className="text-ink-muted">
          {/* 끝나지 않은 것을 또 누르면 같은 사람에게 알림이 두 번 간다. */}
          진행 중에는 다시 실행할 수 없습니다. 끝나면 대상이었던 회원에게 결과 알림이 갑니다.
        </p>
      ) : null}
    </div>
  );
}
