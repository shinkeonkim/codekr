import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type {
  RunResult,
  SubmissionDetail,
  SubmissionSummary,
  SubmissionVisibility,
} from "../model/types";

export const submissionApi = {
  run: (
    slug: string,
    body: { runtimeId: string; sourceCode: string; stdin: string },
  ) =>
    request<RunResult>(`/api/v1/problems/${slug}/run`, {
      method: "POST",
      body,
      auth: true,
    }),

  submit: (
    slug: string,
    /**
     * 소스는 **둘 중 하나**다 (#457).
     *
     * 파일이 여럿인 문제는 `files` 로 내고, 그때 `sourceCode` 는 서버가 진입점 파일에서
     * 채운다. 둘 다 필수로 두면 화면이 파일 문제에서 무엇을 넣어야 할지 알 수 없다.
     */
    body: {
      runtimeId: string;
      sourceCode?: string;
      files?: { name: string; sourceCode: string }[];
      visibility?: SubmissionVisibility;
    },
  ) =>
    request<{ submissionId: number; status: string }>(
      `/api/v1/problems/${slug}/submissions`,
      {
        method: "POST",
        body,
        auth: true,
      },
    ),

  /**
   * 대회에 낸다 (#62, #541).
   *
   * **경로가 평소와 다르다.** 같은 경로에 대회 여부를 실어 보내면 대회가 아닌 척
   * 제출해 평소 큐로 보내는 길이 생긴다 — 서버가 그 이유로 경로를 나눠 두었는데
   * 화면이 그 경로를 부르지 않아 대회에 제출할 방법이 아예 없었다.
   */
  submitToContest: (
    contestSlug: string,
    problemSlug: string,
    body: {
      runtimeId: string;
      sourceCode?: string;
      files?: { name: string; sourceCode: string }[];
      visibility?: SubmissionVisibility;
    },
  ) =>
    request<{ submissionId: number; status: string }>(
      `/api/v1/contests/${encodeURIComponent(contestSlug)}/problems/${encodeURIComponent(problemSlug)}/submissions`,
      { method: "POST", body, auth: true },
    ),

  detail: (id: number) =>
    request<SubmissionDetail>(`/api/v1/submissions/${id}`, { auth: true }),

  changeVisibility: (id: number, visibility: SubmissionVisibility) =>
    request<void>(`/api/v1/submissions/${id}/visibility`, {
      method: "PATCH",
      body: { visibility },
      auth: true,
    }),

  mine: (query: Query) =>
    request<Page<SubmissionSummary>>("/api/v1/submissions", {
      auth: true,
      query,
    }),

  /** 전체 회원의 제출 목록 (#34). 소스 코드는 담기지 않는다. */
  explore: (query: Query) =>
    request<Page<SubmissionSummary>>("/api/v1/submissions/explore", {
      auth: true,
      query,
    }),
};
