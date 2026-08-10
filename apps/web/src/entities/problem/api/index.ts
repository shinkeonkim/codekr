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
