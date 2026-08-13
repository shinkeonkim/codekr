import { request } from "@/shared/api";
import type { Page } from "@/shared/api";
import type { RankingEntry } from "@/entities/ranking";
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

  /**
   * 그룹 안 랭킹 (#402). **멤버만 볼 수 있다** — 그룹의 명단이 곧 그 랭킹이다.
   *
   * 경로가 `/rankings?groupId=` 가 아닌 이유가 그것이다: 접근 규칙이 다른 것을 같은
   * 경로의 질의 인자로 두면 그 규칙이 잊힌다.
   */
  ranking: (id: number, params: { metric: string; period: string; page: number; size: number }) =>
    request<Page<RankingEntry>>(`/api/v1/groups/${id}/rankings`, { auth: true, query: params }),
};
