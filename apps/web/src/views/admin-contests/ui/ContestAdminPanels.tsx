"use client";

import type { AdminContest } from "@/entities/contest";
import { ContestApplicantsPanel } from "./ContestApplicantsPanel";
import { ContestAuditPanel } from "./ContestAuditPanel";
import { ContestOperationsPanel } from "./ContestOperationsPanel";

/**
 * 이미 만들어진 대회에만 있는 것들 (#543, #544, #545).
 *
 * 폼에서 떼어 둔 이유: **만들기 전에는 이 셋이 다 의미가 없다.** 그리고 폼 파일이
 * 폼과 패널 호스팅 두 가지를 하고 있었다.
 */
export function ContestAdminPanels({
  contest,
  requiresApproval,
  onChanged,
}: {
  contest: AdminContest;
  /** 폼에서 방금 바꾼 값. 저장 전에도 안내가 맞게 나와야 한다. */
  requiresApproval: boolean;
  onChanged: (next: AdminContest) => void;
}) {
  return (
    <>
      <ContestApplicantsPanel contestId={contest.id} requiresApproval={requiresApproval} />
      <ContestOperationsPanel contest={contest} onChanged={onChanged} />
      <ContestAuditPanel contestId={contest.id} />
    </>
  );
}
