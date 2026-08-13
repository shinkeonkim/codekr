import { request } from "@/shared/api";
import type { Page } from "@/shared/api";
import type { AffiliationRankingEntry, RankingEntry, RankingOptions } from "../model/types";

export const rankingApi = {
  /**
   * 랭킹은 공개 정보다 — 로그인하지 않아도 볼 수 있다.
   *
   * `affiliationId` 는 **모집단을 좁힐 뿐 정렬을 바꾸지 않는다** (#399). 그래서
   * 등수는 그 안에서 1위부터 다시 매겨진다.
   */
  list: (params: {
    metric: string;
    period: string;
    page: number;
    size: number;
    affiliationId?: number;
  }) =>
    request<Page<RankingEntry>>(
      `/api/v1/rankings?metric=${params.metric}&period=${params.period}&page=${params.page}&size=${params.size}` +
        (params.affiliationId === undefined ? "" : `&affiliationId=${params.affiliationId}`),
    ),

  options: () => request<RankingOptions>("/api/v1/rankings/metrics"),

  /**
   * 소속끼리 겨루는 랭킹 (#400). 지표를 고르지 않는다 — 값이 실력 점수 합으로 정해져 있다.
   */
  affiliations: (params: { period: string; page: number; size: number }) =>
    request<Page<AffiliationRankingEntry>>("/api/v1/rankings/affiliations", { query: params }),

  /** 맞힌 제출이 있는 **모든** 회원을 다시 계산한다 (#177, #180). */
  recomputeAll: () =>
    request<{ users: number }>("/api/v1/admin/ranking/recompute", { method: "POST", auth: true }),

  /** 회원 한 명만 다시 계산한다 (#177, #180). */
  recomputeUser: (userId: number) =>
    request<{ score: number; solvedCount: number }>(
      `/api/v1/admin/users/${userId}/ranking/recompute`,
      { method: "POST", auth: true },
    ),
};
