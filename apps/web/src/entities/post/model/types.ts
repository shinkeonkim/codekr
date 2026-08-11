/** 게시판 (#137). */

export type Board = "FREE" | "QUESTION" | "NOTICE";

export interface BoardOption {
  value: Board;
  label: string;
  description: string;
  /** 이 사람이 이 게시판에 쓸 수 있는가. 쓸 수 없는 곳에 버튼을 보이면 눌렀을 때 거부당한다. */
  writable: boolean;
}

export interface PostSummary {
  id: number;
  board: Board;
  boardLabel: string;
  title: string;
  authorNickname: string;
  authorAvatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
  edited: boolean;
  /** 댓글 수 (#138). */
  commentCount: number;
  /** 문제에 붙은 질문이면 그 문제 (#139). */
  problemSlug: string | null;
  problemTitle: string | null;
}

export interface PostDetail {
  summary: PostSummary;
  /**
   * 코드 블록을 기본으로 가릴지 (#139).
   *
   * 문제 질문에는 정답 코드가 그대로 올라온다. 아직 못 푼 사람에게 답이 보이면
   * 그 문제의 값이 떨어진다.
   */
  hideCode: boolean;
  /** 마크다운 원문. 렌더링은 화면이 한다 — 서버가 HTML 을 만들지 않는다. */
  body: string;
  editable: boolean;
  deletable: boolean;
}
