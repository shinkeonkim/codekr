import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type { CommentTree, Comment } from "../model/comment";
import type { Board, BoardOption, PostDetail, PostSummary } from "../model/types";

export interface PostUpsert {
  board: Board;
  title: string;
  body: string;
  /** 문제에 붙는 질문이면 그 문제 (#139). */
  problemId?: number;
}

export const postApi = {
  /** 읽기는 공개다 — 토큰이 있으면 싣지만 없어도 된다. */
  list: (query: Query = {}) => request<Page<PostSummary>>("/api/v1/posts", { auth: true, query }),

  boards: () => request<BoardOption[]>("/api/v1/posts/boards", { auth: true }),

  /** 한 문제에 붙은 질문 (#139). */
  byProblem: (problemId: number, page = 0) =>
    request<Page<PostSummary>>(`/api/v1/posts/by-problem/${problemId}?page=${page}&size=20`, {
      auth: true,
    }),

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
  /**
   * 댓글 트리 (#213). **잘라서 온다.**
   *
   * `after` 는 마지막으로 받은 댓글 id 다 — 오프셋이면 읽는 사이에 새 댓글이 달릴 때
   * 이미 본 것을 다시 받거나 건너뛴다. `around` 는 알림·링크로 들어온 자리다 (#212).
   */
  comments: (postId: number, query?: { after?: number; around?: number }) =>
    request<CommentTree>(`/api/v1/posts/${postId}/comments`, { auth: true, query }),

  /** 한 부모의 답글을 이어받는다 (#213). */
  commentChildren: (commentId: number, after?: number) =>
    request<CommentTree>(`/api/v1/comments/${commentId}/children`, { auth: true, query: { after } }),

  addComment: (postId: number, body: { parentId?: number; body: string }) =>
    request<CommentTree>(`/api/v1/posts/${postId}/comments`, { method: "POST", body, auth: true }),

  updateComment: (id: number, body: string) =>
    request<CommentTree>(`/api/v1/comments/${id}`, { method: "PUT", body: { body }, auth: true }),

  removeComment: (id: number) =>
    request<CommentTree>(`/api/v1/comments/${id}`, { method: "DELETE", auth: true }),
};
