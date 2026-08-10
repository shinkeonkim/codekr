import { request } from "@/shared/api";
import type { Query } from "@/shared/api";
import type { ActivityResponse } from "../model/types";

export const activityApi = {
  mine: (query: Query = {}) =>
    request<ActivityResponse>("/api/v1/users/me/activity", { auth: true, query }),
};
