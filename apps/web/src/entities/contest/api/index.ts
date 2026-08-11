import { request } from "@/shared/api";
import type { Page } from "@/shared/api";
import type { ContestDetail, ContestSummary, Scoreboard } from "../model/types";

export const contestApi = {
  /** 목록. 공개다. 준비 중인 대회는 서버가 걸러 준다. */
  list: (page = 0, size = 20) =>
    request<Page<ContestSummary>>(`/api/v1/contests?page=${page}&size=${size}`),

  /**
   * 상세. 로그인 여부에 따라 내용이 다르다 — 참가자에게만 문제가 보인다.
   *
   * `auth: true` 는 **토큰이 있으면 싣는다**는 뜻이지 로그인을 요구하는 것이 아니다.
   * 비로그인도 볼 수 있고, 그때는 문제가 비어 온다.
   */
  detail: (slug: string) =>
    request<ContestDetail>(`/api/v1/contests/${encodeURIComponent(slug)}`, { auth: true }),

  register: (slug: string) =>
    request<void>(`/api/v1/contests/${encodeURIComponent(slug)}/registrations`, {
      method: "POST",
      auth: true,
    }),

  /** actual 은 대회 관리자만 의미가 있다. 권한이 없으면 서버가 참가자와 같은 것을 준다. */
  scoreboard: (slug: string, actual = false) =>
    request<Scoreboard>(
      `/api/v1/contests/${encodeURIComponent(slug)}/scoreboard?actual=${actual}`,
      { auth: true },
    ),
};
