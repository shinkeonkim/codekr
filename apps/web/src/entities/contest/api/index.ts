import { request } from "@/shared/api";
import type { Page } from "@/shared/api";
import type {
  ContestDetail,
  ContestNotice,
  ContestQuestion,
  ContestSummary,
  Scoreboard,
} from "../model/types";

export const contestApi = {
  /** 목록. 공개다. 준비 중인 대회는 서버가 걸러 준다. */
  list: (page = 0, size = 20) =>
    request<Page<ContestSummary>>(`/api/v1/contests?page=${page}&size=${size}`),

  /**
   * 내가 신청한 대회 (#546).
   *
   * **비공개 대회를 다시 찾는 유일한 길이다** — 목록에 안 뜨므로 주소를 잃으면
   * 여기 말고는 돌아올 곳이 없다.
   */
  registered: (page = 0, size = 20) =>
    request<Page<ContestSummary>>(`/api/v1/contests/registered?page=${page}&size=${size}`, {
      auth: true,
    }),

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

  /** 공지 (#147). 참가하지 않은 사람도 읽는다 — 끝난 뒤 기록으로 남는다. */
  notices: (slug: string) =>
    request<ContestNotice[]>(`/api/v1/contests/${encodeURIComponent(slug)}/notices`, { auth: true }),

  addNotice: (slug: string, body: { title: string; body: string }) =>
    request<ContestNotice>(`/api/v1/contests/${encodeURIComponent(slug)}/notices`, {
      method: "POST",
      body,
      auth: true,
    }),

  /** 질의. **볼 수 있는 것만** 온다 — 남의 비공개 답변은 내려오지 않는다. */
  questions: (slug: string) =>
    request<ContestQuestion[]>(`/api/v1/contests/${encodeURIComponent(slug)}/questions`, {
      auth: true,
    }),

  ask: (slug: string, body: { problemId?: number; body: string }) =>
    request<ContestQuestion>(`/api/v1/contests/${encodeURIComponent(slug)}/questions`, {
      method: "POST",
      body,
      auth: true,
    }),

  answer: (slug: string, questionId: number, body: { answer: string; public: boolean }) =>
    request<void>(
      `/api/v1/contests/${encodeURIComponent(slug)}/questions/${questionId}/answer`,
      { method: "PUT", body, auth: true },
    ),
};

/** 공개 범위 (#465). **`status` 와 다른 값이다** — 그쪽은 "준비 중인가" 다. */
export type ContestVisibility = "PUBLIC" | "UNLISTED";

export interface AdminContest {
  id: number;
  slug: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  status: string;
  phase: string;
  phaseLabel: string;
  freezeMinutes: number;
  submissionCooldownSeconds: number;
  visibility: ContestVisibility;
}

export interface ContestUpsert {
  slug: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  freezeMinutes: number;
  submissionCooldownSeconds: number;
  visibility: ContestVisibility;
}

/**
 * 대회 관리 (#335).
 *
 * API 는 전부 있었는데 **화면이 없어서 `CONTEST_MANAGER` 가 할 일이 없었다.**
 */
export const adminContestApi = {
  list: (query: { page?: number; size?: number }) =>
    request<Page<AdminContest>>("/api/v1/admin/contests", { auth: true, query }),

  detail: (id: number) => request<AdminContest>(`/api/v1/admin/contests/${id}`, { auth: true }),

  create: (body: ContestUpsert) =>
    request<AdminContest>("/api/v1/admin/contests", { method: "POST", auth: true, body }),

  /** **진행 중인 대회는 서버가 막는다** (#335) — 화면도 막지만 그것만으로는 부족하다. */
  update: (id: number, body: ContestUpsert) =>
    request<AdminContest>(`/api/v1/admin/contests/${id}`, { method: "PUT", auth: true, body }),

  changeStatus: (id: number, status: string) =>
    request<AdminContest>(`/api/v1/admin/contests/${id}/status`, {
      method: "PUT",
      auth: true,
      query: { status },
    }),
};
