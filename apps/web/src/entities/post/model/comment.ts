/** 댓글 (#138). */

/**
 * 잘라서 오는 댓글 트리 (#213).
 *
 * 전체 수를 **서버가 센다** — 받은 트리를 화면이 세던 방식은 잘라 내리기 시작하면
 * 곧바로 틀린 수가 된다.
 */
export interface CommentTree {
  comments: Comment[];
  totalCount: number;
  remainingTop: number;
}

export interface Comment {
  id: number;
  /** 어느 댓글에 달린 답인지 (#213). 이어받은 것을 제자리에 끼워 넣을 때 쓴다. */
  parentId: number | null;
  authorNickname: string | null;
  authorAvatarUrl: string | null;
  /** 삭제된 댓글은 본문이 오지 않는다. 자리만 남는다. */
  body: string | null;
  deleted: boolean;
  createdAt: string;
  /**
   * 고친 시각 (#211). 고친 적이 없으면 null.
   *
   * `edited` 만으로는 부족하다 — 답이 달린 뒤에 원글을 고치면 대화가 어긋나 보이는데,
   * 언제 고쳤는지가 없으면 답글 쓴 사람이 잘못 읽은 것처럼 된다.
   */
  editedAt: string | null;
  edited: boolean;
  editable: boolean;
  deletable: boolean;
  children: Comment[];
  /** 아직 안 내려온 답글 수 (#213). 0 이면 다 왔다. */
  remainingChildren: number;
  /**
   * 본문이 부른 사람들 (#214).
   *
   * **본문과 함께 온다** — 화면이 id 로 다시 조회하면 댓글 수만큼 요청이 나가고,
   * 하나라도 실패하면 멘션이 저장 표기 그대로 보인다.
   */
  mentions: { id: number; nickname: string }[];
}

/**
 * 화면에서 들여쓰기를 멈추는 깊이 (#138).
 *
 * **저장에는 제한이 없다.** 깊어질수록 들여쓰기가 화면을 밀어내므로 여기서만 접는다 —
 * 이 깊이를 넘으면 더 들여쓰지 않고 같은 자리에 쌓는다.
 */
export const MAX_VISUAL_DEPTH = 4;
