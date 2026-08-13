import { request } from "@/shared/api";
import type { Page } from "@/shared/api";
import type { CollectionDetail, CollectionSummary, CollectionVisibility } from "../model/types";

export interface CollectionUpsert {
  name: string;
  description: string;
  visibility: CollectionVisibility;
  /** 순서가 곧 문제집의 순서다. */
  problemIds: number[];
}

export const collectionApi = {
  /** 공개 문제집 목록 (#208). 로그인 없이 열린다 — 발견되지 않으면 공개의 뜻이 없다. */
  publicList: (query: { page?: number; size?: number }) =>
    request<Page<CollectionSummary>>("/api/v1/collections", { query }),

  /** 어드민이 목록에서 내린다 (#208). **지우지 않는다** — 비공개로 되돌린다. */
  takedown: (id: number, reason: string) =>
    request<void>(`/api/v1/admin/collections/${id}/takedown`, {
      method: "POST",
      auth: true,
      query: { reason },
    }),

  mine: () => request<CollectionSummary[]>("/api/v1/collections/me", { auth: true }),

  detail: (id: number) => request<CollectionDetail>(`/api/v1/collections/${id}`, { auth: true }),

  /** 링크 공유. 로그인 없이도 열린다. */
  shared: (shareToken: string) =>
    request<CollectionDetail>(`/api/v1/collections/shared/${encodeURIComponent(shareToken)}`, {
      auth: true,
    }),

  create: (body: CollectionUpsert) =>
    request<CollectionDetail>("/api/v1/collections", { method: "POST", body, auth: true }),

  update: (id: number, body: CollectionUpsert) =>
    request<CollectionDetail>(`/api/v1/collections/${id}`, { method: "PUT", body, auth: true }),

  remove: (id: number) =>
    request<void>(`/api/v1/collections/${id}`, { method: "DELETE", auth: true }),
};
