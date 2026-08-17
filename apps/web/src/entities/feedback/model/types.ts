/** 사이트 신고·제안 (#603). 문제에 매인 오류 신고(#478)와는 다른 것이다. */
export type FeedbackKind = "BUG" | "SUGGESTION" | "OTHER";

export type FeedbackStatus = "OPEN" | "ACCEPTED" | "REJECTED";

export interface SiteFeedback {
  id: number;
  reporterId: number;
  reporterNickname: string;
  kind: FeedbackKind;
  kindLabel: string;
  body: string;
  pageUrl: string | null;
  status: FeedbackStatus;
  statusLabel: string;
  resolution: string | null;
  createdAt: string;
}

/** 넣는 사람이 고르는 것. 서버 `FeedbackKind` 와 같은 순서를 지킨다. */
export const FEEDBACK_KINDS: readonly { readonly value: FeedbackKind; readonly label: string }[] = [
  { value: "BUG", label: "안 됩니다" },
  { value: "SUGGESTION", label: "이렇게 해 주세요" },
  { value: "OTHER", label: "그 밖에" },
];
