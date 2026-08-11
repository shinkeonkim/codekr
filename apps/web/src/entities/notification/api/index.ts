import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type { Notification } from "../model/types";

export const notificationApi = {
  list: (query: Query = {}) => request<Page<Notification>>("/api/v1/notifications", { auth: true, query }),

  /**
   * 헤더 뱃지용. 목록 전체를 받지 않고 숫자만 자주 확인한다.
   *
   * `byCategory` 는 탭 배지에 쓴다 (#135). 목록 화면이 같은 것을 또 부르지 않게
   * 서버가 함께 내린다.
   */
  unreadCount: () =>
    request<{ unreadCount: number; byCategory: Record<string, number> }>(
      "/api/v1/notifications/unread-count",
      { auth: true },
    ),

  markRead: (id: number) =>
    request<void>(`/api/v1/notifications/${id}/read`, { method: "POST", auth: true }),

  /**
   * 모두 읽음. **카테고리를 주면 그 탭만 읽는다** (#135).
   *
   * 채점 탭에서 눌렀는데 대회 알림까지 읽음이 되면 보지 않은 것을 읽은 것으로
   * 만든 셈이고, 되돌릴 수 없다.
   */
  markAllRead: (category?: string) =>
    request<void>("/api/v1/notifications/read-all", {
      method: "POST",
      auth: true,
      query: category ? { category } : {},
    }),
};
