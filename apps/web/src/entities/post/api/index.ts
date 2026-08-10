import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type { Comment } from "../model/comment";
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

  /**
   * 댓글 (#138).
   *
   * 쓰기·수정·삭제 응답이 **트리 전체**다. 한 건만 받으면 화면이 트리의 어디에 끼워
   * 넣을지 다시 계산해야 하고, 그 규칙이 서버와 갈라진다.
   */
  comments: (postId: number) => request<Comment[]>(`/api/v1/posts/${postId}/comments`, { auth: true }),

  addComment: (postId: number, body: { parentId?: number; body: string }) =>
    request<Comment[]>(`/api/v1/posts/${postId}/comments`, { method: "POST", body, auth: true }),

  updateComment: (id: number, body: string) =>
    request<Comment[]>(`/api/v1/comments/${id}`, { method: "PUT", body: { body }, auth: true }),

  removeComment: (id: number) =>
    request<Comment[]>(`/api/v1/comments/${id}`, { method: "DELETE", auth: true }),
};
