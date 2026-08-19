import type { PendingApplicant } from "@/entities/contest";
import { formatDateTime } from "@/shared/lib";
import { Button } from "@/shared/ui";
import type { Column } from "@/shared/ui";

/**
 * 참가 신청자 목록의 열 (#634).
 *
 * **신청 시각이 한 세로줄에 서야 순서를 읽는다.** 카드일 때는 닉네임 길이를 따라
 * 시각이 줄마다 다른 자리에 떠서, "누가 먼저 신청했나" 를 훑을 수 없었다. 대회는
 * 시작 시각이 있어 **한 번에 여러 명을 보고 처리하는 화면**이다.
 */
export function applicantColumns(
  rejectingId: number | null,
  busyId: number | null,
  onApprove: (applicant: PendingApplicant) => void,
  onToggleReject: (applicant: PendingApplicant) => void,
): Column<PendingApplicant>[] {
  return [
    {
      key: "nickname",
      header: "닉네임",
      render: (applicant) => <span className="font-medium text-ink">{applicant.nickname}</span>,
    },
    {
      key: "handle",
      header: "핸들",
      hideBelow: "sm",
      render: (applicant) => <span className="text-xs text-ink-muted">@{applicant.handle}</span>,
    },
    {
      key: "appliedAt",
      header: "신청 시각",
      align: "center",
      render: (applicant) => (
        <span className="whitespace-nowrap text-xs text-ink-muted">{formatDateTime(applicant.appliedAt)}</span>
      ),
    },
    {
      key: "actions",
      header: "작업",
      align: "right",
      render: (applicant) => (
        <span className="flex justify-end gap-2">
          {/*
            **한 사람을 처리하는 동안 그 줄만 잠근다** (#634). 전에는 `busy` 하나로
            목록 전체가 잠겨, 한 명을 승인하는 사이 다른 줄도 누를 수 없었다.
          */}
          <Button
            className="whitespace-nowrap px-3 py-1 text-xs"
            disabled={busyId === applicant.userId}
            onClick={() => onApprove(applicant)}
          >
            승인
          </Button>
          <Button
            variant="secondary"
            className="whitespace-nowrap px-3 py-1 text-xs"
            disabled={busyId === applicant.userId}
            onClick={() => onToggleReject(applicant)}
          >
            {rejectingId === applicant.userId ? "취소" : "거절"}
          </Button>
        </span>
      ),
    },
  ];
}
