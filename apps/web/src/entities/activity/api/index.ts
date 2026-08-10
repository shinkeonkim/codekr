import { request } from "@/shared/api";
import type { Query } from "@/shared/api";
import type { ActivityResponse } from "../model/types";

export const activityApi = {
  /** [year] 를 주면 그 해 전체를 본다 (#81). */
  mine: (query: Query = {}) =>
    request<ActivityResponse>("/api/v1/users/me/activity", { auth: true, query }),
};
