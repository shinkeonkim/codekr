import { request } from "@/shared/api";
import type { StatsDrift } from "../model/types";

export const problemStatsApi = {
  /** 어긋난 문제만 돌려준다. 아무것도 없으면 빈 배열이다. */
  drift: () => request<StatsDrift[]>("/api/v1/admin/problems/stats/drift", { auth: true }),

  /** 전부 다시 만든다. 여러 번 눌러도 결과가 같다. */
  recompute: () =>
    request<{ problems: number }>("/api/v1/admin/problems/stats/recompute", {
      method: "POST",
      auth: true,
    }),
};
