import { request } from "@/shared/api";
import type { Query } from "@/shared/api";
import type { ActivityResponse } from "../model/types";

export const activityApi = {
  /** [year] 를 주면 그 해 전체를 본다 (#81). */
  mine: (query: Query = {}) =>
    request<ActivityResponse>("/api/v1/users/me/activity", { auth: true, query }),

  /** 남의 활동 (#117). 프로필과 같은 공개 범위 — 로그인이 필요하다. */
  ofUser: (nickname: string, query: Query = {}) =>
    request<ActivityResponse>(`/api/v1/users/${encodeURIComponent(nickname)}/activity`, {
      auth: true,
      query,
    }),
};
