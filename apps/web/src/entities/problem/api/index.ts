import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type {
  AdminProblemDetail,
  ProblemDetail,
  ProblemBundlePreview,
  ProblemImportResult,
  ProblemSummary,
  QuizResult,
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
   * 퀴즈 답을 내고 **그 자리에서** 채점받는다 (#650).
   *
   * 코드 제출(`/submissions`)과 경로를 나눈 이유: 저쪽은 202 로 접수만 하고 판정은
   * 소켓으로 오는데, 여기는 응답에 결과와 해설이 함께 온다.
   */
  submitQuiz: (slug: string, body: { selected: number[]; text: string | null }) =>
    request<QuizResult>(`/api/v1/problems/${slug}/quiz`, { method: "POST", body, auth: true }),

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

  /**
   * 고른 문제들의 공개 여부를 한 번에 (#627).
   *
   * **`update` 를 쓰지 않는다.** 그쪽은 문제 전체를 덮어써서 공개 여부만 바꿔도
   * 테스트케이스가 지워졌다 다시 들어간다.
   */
  publish: (ids: number[], published: boolean) =>
    request<PublishResult>("/api/v1/admin/problems/publish", {
      method: "POST",
      body: { ids, published },
      auth: true,
    }),
};

/** 몇 개가 실제로 바뀌었나. 이미 그 상태였던 것은 `changed` 에 들어가지 않는다. */
export interface PublishResult {
  requested: number;
  changed: number;
  missing: number[];
}

/**
 * 첫 화면이 보여 줄 숫자 (#231).
 *
 * **부끄럽지 않은 것만 온다** — 문제 수와 지원 언어 수다. 회원 수·제출 수는 초기에
 * 비어 보이므로 서버가 내리지 않는다.
 */
export const siteStatsApi = {
  fetch: () => request<{ problemCount: number; runtimeCount: number }>("/api/v1/stats/site"),
};
