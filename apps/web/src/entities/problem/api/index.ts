import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type {
  AdminProblemDetail,
  ProblemDetail,
  ProblemBundlePreview,
  ProblemImportResult,
  ProblemSummary,
  ProblemVerification,
  Runtime,
} from "../model/types";

/** multipart 는 Content-Type 을 손대지 않는다 — 경계 문자열은 브라우저가 만든다 (#389). */
function toForm(file: File): FormData {
  const form = new FormData();
  form.append("file", file);
  return form;
}

export const problemApi = {
  list: (query: Query) => request<Page<ProblemSummary>>("/api/v1/problems", { query }),

  detail: (slug: string) => request<ProblemDetail>(`/api/v1/problems/${slug}`),

  /**
   * 실행 환경 목록 (#60, #448). 유형으로 거른다.
   *
   * `JUDGE_FUNCTION` 은 **실행기가 하네스 방식을 아는 언어만** 돌려준다 (#446).
   */
  runtimes: (problemKind = "JUDGE_STDIO") =>
    request<Runtime[]>("/api/v1/runtimes", { query: { problemKind } }),

  /**
   * 묶음을 읽어 무엇이 들어올지만 본다 (#537). **아무것도 만들지 않는다.**
   *
   * zip 과 맨 JSON 을 모두 받는다 — 서버가 매직 바이트로 가른다.
   */
  importPreview: (file: File) =>
    request<ProblemBundlePreview>("/api/v1/admin/problems/imports/preview", {
      method: "POST",
      body: toForm(file),
      auth: true,
    }),

  /**
   * 묶음으로 문제를 만든다 (#479, #537).
   *
   * 미리보기 뒤에도 **파일을 다시 올린다** — 서버가 올린 것을 들고 있지 않기 때문이다.
   */
  importBundle: (file: File) =>
    request<ProblemImportResult>("/api/v1/admin/problems/imports", {
      method: "POST",
      body: toForm(file),
      auth: true,
    }),

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
