import { request } from "@/shared/api";
import type { RejudgeBatch, RejudgeStatus } from "../model/types";

export const rejudgeApi = {
  /** 누르기 전에 대상 수와 진행 중인 배치를 본다 (#219). */
  status: (problemId: number) =>
    request<RejudgeStatus>(`/api/v1/admin/problems/${problemId}/rejudge`, { auth: true }),

  /** 이유는 필수다 — 알림 본문에 그대로 들어간다 (#107). */
  run: (problemId: number, reason: string) =>
    request<RejudgeBatch>(`/api/v1/admin/problems/${problemId}/rejudge`, {
      method: "POST",
      auth: true,
      body: { reason },
    }),
};
