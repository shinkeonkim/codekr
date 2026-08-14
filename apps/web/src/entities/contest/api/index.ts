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

  /**
   * 공지를 지운다 (#544). 대회 관리자만.
   *
   * 대회 중 공지는 참가자 전원이 본다 — **오타 하나가 크다.**
   */
  deleteNotice: (slug: string, noticeId: number) =>
    request<void>(`/api/v1/contests/${encodeURIComponent(slug)}/notices/${noticeId}`, {
      method: "DELETE",
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
  requiresApproval: boolean;
  /** 지금 순위가 동결돼 있는가 (#86). */
  frozen: boolean;
  /** 최종 순위를 공개한 시각. `null` 이면 아직 안 풀었다 (#544). */
  unfrozenAt: string | null;
  problems: AdminContestProblem[];
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
  /**
   * 신청을 승인해야 참가되는가 (#466).
   *
   * **켤 수는 있는데 승인할 화면이 없었다** (#543) — 신청한 사람은 영원히 대기했다.
   */
  requiresApproval: boolean;
}

/** 대회에 붙인 문제 (#544). `excluded` 면 순위 계산에서 빠진다. */
export interface AdminContestProblem {
  problemId: number;
  label: string;
  slug: string;
  title: string;
  seq: number;
  score: number;
  excluded: boolean;
}

/** 승인을 기다리는 신청자 (#466, #543). */
export interface PendingApplicant {
  userId: number;
  nickname: string;
  handle: string;
  appliedAt: string;
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

  /** 최종 순위 공개 (#86, #544). **끝난 뒤에만** 되고 다시 얼릴 수 없다. */
  unfreeze: (id: number) =>
    request<AdminContest>(`/api/v1/admin/contests/${id}/unfreeze`, { method: "POST", auth: true }),

  /** 문제를 대회에서 빼거나 되돌린다 (#544). 순위 계산에서 빠진다. */
  excludeProblem: (id: number, problemId: number, excluded: boolean) =>
    request<AdminContest>(`/api/v1/admin/contests/${id}/problems/${problemId}/exclusion`, {
      method: "PUT",
      auth: true,
      query: { excluded: String(excluded) },
    }),

  /** **준비 중인 대회만** 지워진다 — 서버가 그 밖을 거절한다 (제출 이력이 딸려 있다). */
  remove: (id: number) =>
    request<void>(`/api/v1/admin/contests/${id}`, { method: "DELETE", auth: true }),

  /** 승인을 기다리는 신청자 (#543). 승인이 꺼진 대회에서는 늘 비어 있다. */
  applicants: (id: number) =>
    request<PendingApplicant[]>(`/api/v1/admin/contests/${id}/applicants`, { auth: true }),

  approve: (id: number, userId: number) =>
    request<void>(`/api/v1/admin/contests/${id}/applicants/${userId}/approval`, {
      method: "POST",
      auth: true,
    }),

  /**
   * 거절한다. **사유가 필수다** — 서버가 신청 행을 지우므로 그것이 유일한 설명이다.
   */
  reject: (id: number, userId: number, reason: string) =>
    request<void>(`/api/v1/admin/contests/${id}/applicants/${userId}`, {
      method: "DELETE",
      auth: true,
      query: { reason },
    }),
};
