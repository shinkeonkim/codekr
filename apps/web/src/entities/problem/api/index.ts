import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type {
  AdminProblemDetail,
  ProblemDetail,
  ProblemSummary,
  ProblemVerification,
  Runtime,
} from "../model/types";

export const problemApi = {
  list: (query: Query) => request<Page<ProblemSummary>>("/api/v1/problems", { query }),

  detail: (slug: string) => request<ProblemDetail>(`/api/v1/problems/${slug}`),

  runtimes: () => request<Runtime[]>("/api/v1/runtimes"),

  adminList: (query: Query) =>
    request<Page<ProblemSummary>>("/api/v1/admin/problems", { auth: true, query }),

  adminDetail: (id: number) =>
    request<AdminProblemDetail>(`/api/v1/admin/problems/${id}`, { auth: true }),

  create: (body: unknown) =>
    request<{ id: number; slug: string }>("/api/v1/admin/problems", {
      method: "POST",
      body,
      auth: true,
    }),

  update: (id: number, body: unknown) =>
    request<AdminProblemDetail>(`/api/v1/admin/problems/${id}`, { method: "PUT", body, auth: true }),

  verify: (id: number) =>
    request<ProblemVerification>(`/api/v1/admin/problems/${id}/verify`, {
      method: "POST",
      auth: true,
    }),

  remove: (id: number) =>
    request<void>(`/api/v1/admin/problems/${id}`, { method: "DELETE", auth: true }),
};

/**
 * 첫 화면이 보여 줄 숫자 (#231).
 *
 * **부끄럽지 않은 것만 온다** — 문제 수와 지원 언어 수다. 회원 수·제출 수는 초기에
 * 비어 보이므로 서버가 내리지 않는다.
 */
export const siteStatsApi = {
  fetch: () => request<{ problemCount: number; runtimeCount: number }>("/api/v1/stats/site"),
};
