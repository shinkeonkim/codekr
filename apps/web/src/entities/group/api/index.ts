import { request } from "@/shared/api";
import type { GroupDetail, GroupInvitePreview, GroupSummary } from "../model/types";

/** 그룹 API (#401). **전부 로그인이 필요하다** — 초대 미리보기도 그렇다. */
export const groupApi = {
  mine: () => request<GroupSummary[]>("/api/v1/groups", { auth: true }),

  create: (body: { name: string; description: string; openJoin: boolean }) =>
    request<GroupDetail>("/api/v1/groups", { method: "POST", auth: true, body }),

  detail: (id: number) => request<GroupDetail>(`/api/v1/groups/${id}`, { auth: true }),

  update: (id: number, body: { name: string; description: string; openJoin: boolean }) =>
    request<GroupDetail>(`/api/v1/groups/${id}`, { method: "PATCH", auth: true, body }),

  /** 해산한다. 방장만. 행은 지우지 않는다 (ADR-0007). */
  remove: (id: number) => request<void>(`/api/v1/groups/${id}`, { method: "DELETE", auth: true }),

  /** 링크를 새로 뽑는다. **옛 링크는 그 자리에서 죽는다.** */
  rotateInvite: (id: number) =>
    request<{ inviteToken: string }>(`/api/v1/groups/${id}/invite`, { method: "POST", auth: true }),

  preview: (token: string) =>
    request<GroupInvitePreview>(`/api/v1/groups/invites/${token}`, { auth: true }),

  joinByInvite: (token: string) =>
    request<{ groupId: number }>(`/api/v1/groups/invites/${token}/join`, {
      method: "POST",
      auth: true,
    }),

  leave: (id: number) =>
    request<void>(`/api/v1/groups/${id}/members/me`, { method: "DELETE", auth: true }),

  kick: (id: number, userId: number) =>
    request<void>(`/api/v1/groups/${id}/members/${userId}`, { method: "DELETE", auth: true }),

  transferOwner: (id: number, userId: number) =>
    request<void>(`/api/v1/groups/${id}/owner/${userId}`, { method: "POST", auth: true }),
};
