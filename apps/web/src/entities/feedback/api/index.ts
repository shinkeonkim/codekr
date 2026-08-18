import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type { FeedbackKind, FeedbackStatus, SiteFeedback } from "../model/types";

export const feedbackApi = {
  /**
   * 신고·제안을 넣는다 (#603).
   *
   * **로그인이 필요하다.** 막을 것이 없는 채로 열면 스팸이 그대로 쌓이고, 처리 결과를
   * 되돌려 줄 곳도 없다.
   */
  submit: (body: { kind: FeedbackKind; body: string; pageUrl?: string }) =>
    request<SiteFeedback>("/api/v1/feedbacks", { method: "POST", auth: true, body }),

  /** 내가 넣은 것. **어디로 갔는지 볼 수 있어야 다시 넣는다.** */
  mine: (query: Query) =>
    request<Page<SiteFeedback>>("/api/v1/feedbacks/me", { auth: true, query }),

  list: (query: Query) =>
    request<Page<SiteFeedback>>("/api/v1/admin/feedbacks", { auth: true, query }),

  /** 처리한다. **반영하지 않을 때 이유가 중요하다** — 없으면 안 읽은 것과 같다. */
  resolve: (id: number, body: { status: FeedbackStatus; resolution?: string }) =>
    request<SiteFeedback>(`/api/v1/admin/feedbacks/${id}/resolution`, {
      method: "POST",
      auth: true,
      body,
    }),
};
