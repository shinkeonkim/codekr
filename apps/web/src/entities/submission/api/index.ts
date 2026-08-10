import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type {
  RunResult,
  SubmissionDetail,
  SubmissionSummary,
  SubmissionVisibility,
} from "../model/types";

export const submissionApi = {
  run: (slug: string, body: { runtimeId: string; sourceCode: string; stdin: string }) =>
    request<RunResult>(`/api/v1/problems/${slug}/run`, { method: "POST", body, auth: true }),

  submit: (
    slug: string,
    body: { runtimeId: string; sourceCode: string; visibility?: SubmissionVisibility },
  ) =>
    request<{ submissionId: number; status: string }>(`/api/v1/problems/${slug}/submissions`, {
      method: "POST",
      body,
      auth: true,
    }),

  detail: (id: number) => request<SubmissionDetail>(`/api/v1/submissions/${id}`, { auth: true }),

  changeVisibility: (id: number, visibility: SubmissionVisibility) =>
    request<void>(`/api/v1/submissions/${id}/visibility`, {
      method: "PATCH",
      body: { visibility },
      auth: true,
    }),

  mine: (query: Query) =>
    request<Page<SubmissionSummary>>("/api/v1/submissions", { auth: true, query }),

  /** 전체 회원의 제출 목록 (#34). 소스 코드는 담기지 않는다. */
  explore: (query: Query) =>
    request<Page<SubmissionSummary>>("/api/v1/submissions/explore", { auth: true, query }),
};
