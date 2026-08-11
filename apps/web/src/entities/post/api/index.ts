import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type { Board, BoardOption, PostDetail, PostSummary } from "../model/types";

export interface PostUpsert {
  board: Board;
  title: string;
  body: string;
}

export const postApi = {
  /** 읽기는 공개다 — 토큰이 있으면 싣지만 없어도 된다. */
  list: (query: Query = {}) => request<Page<PostSummary>>("/api/v1/posts", { auth: true, query }),

  boards: () => request<BoardOption[]>("/api/v1/posts/boards", { auth: true }),

  detail: (id: number) => request<PostDetail>(`/api/v1/posts/${id}`, { auth: true }),

  create: (body: PostUpsert) =>
    request<PostDetail>("/api/v1/posts", { method: "POST", body, auth: true }),

  update: (id: number, body: PostUpsert) =>
    request<PostDetail>(`/api/v1/posts/${id}`, { method: "PUT", body, auth: true }),

  remove: (id: number) => request<void>(`/api/v1/posts/${id}`, { method: "DELETE", auth: true }),
};
