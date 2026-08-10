import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type { Notification } from "../model/types";

export const notificationApi = {
  list: (query: Query = {}) => request<Page<Notification>>("/api/v1/notifications", { auth: true, query }),

  /** 헤더 뱃지용. 목록 전체를 받지 않고 숫자만 자주 확인한다. */
  unreadCount: () =>
    request<{ unreadCount: number }>("/api/v1/notifications/unread-count", { auth: true }),

  markRead: (id: number) =>
    request<void>(`/api/v1/notifications/${id}/read`, { method: "POST", auth: true }),

  markAllRead: () => request<void>("/api/v1/notifications/read-all", { method: "POST", auth: true }),
};
