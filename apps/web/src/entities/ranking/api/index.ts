import { request } from "@/shared/api";
import type { Page } from "@/shared/api";
import type { RankingEntry, RankingOptions } from "../model/types";

export const rankingApi = {
  /** 랭킹은 공개 정보다 — 로그인하지 않아도 볼 수 있다. */
  list: (params: { metric: string; period: string; page: number; size: number }) =>
    request<Page<RankingEntry>>(
      `/api/v1/rankings?metric=${params.metric}&period=${params.period}&page=${params.page}&size=${params.size}`,
    ),

  options: () => request<RankingOptions>("/api/v1/rankings/metrics"),
};
