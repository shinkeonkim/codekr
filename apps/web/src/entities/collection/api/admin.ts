import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";

/** 어드민 목록 한 줄 (#393). 무엇을 볼지 정하는 데 필요한 것만 온다. */
export interface AdminCollectionRow {
  id: number;
  name: string;
  visibility: string;
  visibilityLabel: string;
  ownerNickname: string;
  problemCount: number;
  createdAt: string;
}

export interface AdminCollectionProblem {
  problemId: number;
  slug: string;
  title: string;
}

export interface AdminCollectionDetail {
  id: number;
  name: string;
  description: string;
  visibility: string;
  visibilityLabel: string;
  ownerNickname: string;
  createdAt: string;
  problems: AdminCollectionProblem[];
}

export const adminCollectionApi = {
  /** **비공개는 오지 않는다** — 서버가 조건 자체에서 뺀다 (#393). */
  list: (query: Query) =>
    request<Page<AdminCollectionRow>>("/api/v1/admin/collections", { auth: true, query }),
  detail: (id: number) =>
    request<AdminCollectionDetail>(`/api/v1/admin/collections/${id}`, { auth: true }),
  /** 지우지 않고 비공개로 되돌린다. 주인에게는 사유와 함께 알림이 간다. */
  takedown: (id: number, reason: string) =>
    request<void>(`/api/v1/admin/collections/${id}/takedown`, {
      method: "POST",
      auth: true,
      query: { reason },
    }),
};
