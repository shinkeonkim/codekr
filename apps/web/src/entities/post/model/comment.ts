/** 댓글 (#138). */

export interface Comment {
  id: number;
  authorNickname: string | null;
  authorAvatarUrl: string | null;
  /** 삭제된 댓글은 본문이 오지 않는다. 자리만 남는다. */
  body: string | null;
  deleted: boolean;
  createdAt: string;
  edited: boolean;
  editable: boolean;
  deletable: boolean;
  children: Comment[];
}

/**
 * 화면에서 들여쓰기를 멈추는 깊이 (#138).
 *
 * **저장에는 제한이 없다.** 깊어질수록 들여쓰기가 화면을 밀어내므로 여기서만 접는다 —
 * 이 깊이를 넘으면 더 들여쓰지 않고 같은 자리에 쌓는다.
 */
export const MAX_VISUAL_DEPTH = 4;
