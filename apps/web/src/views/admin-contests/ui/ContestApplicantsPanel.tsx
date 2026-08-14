"use client";

import { adminContestApi } from "@/entities/contest";
import type { PendingApplicant } from "@/entities/contest";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Alert, Button, Card, CardTitle, EmptyState, Input, useToast } from "@/shared/ui";
import { useCallback, useEffect, useState } from "react";

/**
 * 대회 참가 신청을 승인한다 (#466, #543).
 *
 * **승인해야 참가되는 대회를 만들 수 있는데 승인할 화면이 없었다.** 신청한 사람은
 * 영원히 대기했고, 운영자는 누가 신청했는지도 몰랐다 — #466 이 "누르면 바로 참가되는
 * 것" 을 고치려고 만든 문턱이 **아무도 통과할 수 없는 문**이 되어 있었다.
 *
 * **거절에는 사유가 필수다.** 서버가 신청 행을 지우므로 그것이 신청자에게 남는 유일한
 * 설명이다 — 화면도 사유 없이는 못 누르게 한다.
 */
export function ContestApplicantsPanel({
  contestId,
  requiresApproval,
}: {
  contestId: number;
  requiresApproval: boolean;
}) {
  const toast = useToast();
  const [applicants, setApplicants] = useState<PendingApplicant[] | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    adminContestApi
      .applicants(contestId)
      .then(setApplicants)
      .catch(() => setApplicants([]));
  }, [contestId]);

  useEffect(load, [load]);

  const act = async (action: () => Promise<void>, done: string, fallback: string) => {
    setBusy(true);
    try {
      await action();
      toast.success(done);
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : fallback);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div className="flex flex-wrap items-center gap-2">
        <CardTitle>참가 신청</CardTitle>
        {applicants && applicants.length > 0 ? (
          <span className="text-xs text-ink-muted">{applicants.length}명 대기 중</span>
        ) : null}
      </div>

      {/*
        승인이 꺼져 있으면 신청은 곧바로 참가가 된다 — 이 자리는 늘 비어 있다.
        그 사실을 말해 주지 않으면 "신청이 안 들어온다" 고 오해한다.
      */}
      {!requiresApproval ? (
        <Alert tone="muted">
          이 대회는 승인 없이 바로 참가됩니다. 승인을 받으려면 대회 설정에서 켜십시오.
        </Alert>
      ) : null}

      {applicants === null ? (
        <p className="text-sm text-ink-muted">불러오는 중…</p>
      ) : applicants.length === 0 ? (
        <EmptyState title="대기 중인 신청이 없습니다." />
      ) : (
        <ul className="space-y-2">
          {applicants.map((applicant) => (
            <ApplicantRow
              key={applicant.userId}
              applicant={applicant}
              busy={busy}
              onApprove={() =>
                act(
                  () => adminContestApi.approve(contestId, applicant.userId),
                  `${applicant.nickname} 님을 승인했습니다.`,
                  "승인하지 못했습니다.",
                )
              }
              onReject={(reason) =>
                act(
                  () => adminContestApi.reject(contestId, applicant.userId, reason),
                  `${applicant.nickname} 님의 신청을 거절했습니다.`,
                  "거절하지 못했습니다.",
                )
              }
            />
          ))}
        </ul>
      )}
    </Card>
  );
}

function ApplicantRow({
  applicant,
  busy,
  onApprove,
  onReject,
}: {
  applicant: PendingApplicant;
  busy: boolean;
  onApprove: () => void;
  onReject: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  const [rejecting, setRejecting] = useState(false);

  return (
    <li className="rounded-lg border border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-ink">{applicant.nickname}</span>
        <span className="text-xs text-ink-muted">@{applicant.handle}</span>
        <span className="ml-auto text-xs text-ink-muted">{formatDateTime(applicant.appliedAt)} 신청</span>
      </div>

      {rejecting ? (
        <div className="mt-2 space-y-2">
          <Input
            value={reason}
            placeholder="거절 사유 (신청자에게 남는 유일한 설명입니다)"
            onChange={(event) => setReason(event.target.value)}
          />
          <div className="flex flex-wrap gap-2">
            {/* 사유가 없으면 못 누른다. 서버도 필수로 받는다. */}
            <Button variant="danger" disabled={busy || reason.trim() === ""} onClick={() => onReject(reason.trim())}>
              거절하기
            </Button>
            <Button variant="secondary" disabled={busy} onClick={() => setRejecting(false)}>
              취소
            </Button>
          </div>
        </div>
      ) : (
        <div className="mt-2 flex flex-wrap gap-2">
          <Button disabled={busy} onClick={onApprove}>
            승인
          </Button>
          <Button variant="secondary" disabled={busy} onClick={() => setRejecting(true)}>
            거절
          </Button>
        </div>
      )}
    </li>
  );
}
