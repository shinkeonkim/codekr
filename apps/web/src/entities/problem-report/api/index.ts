import { request } from "@/shared/api";
import type { Page, Query } from "@/shared/api";
import type { ProblemReport, ReportKind, ReportStatus } from "../model/types";

export const problemReportApi = {
  /** 문제의 오류를 신고한다 (#478). 로그인이 필요하다. */
  report: (slug: string, body: { kind: ReportKind; body: string }) =>
    request<ProblemReport>(`/api/v1/problems/${encodeURIComponent(slug)}/reports`, {
      method: "POST",
      auth: true,
      body,
    }),

  /** 들어온 신고 (#548). 상태로 거른다 — 기본은 아직 안 본 것만 본다. */
  list: (query: Query) =>
    request<Page<ProblemReport>>("/api/v1/admin/problem-reports", { auth: true, query }),

  /**
   * 처리한다 (#548).
   *
   * **기각할 때 설명이 중요하다** — "문제 없음" 만 보이면 신고한 사람은 왜인지 모른다.
   */
  resolve: (id: number, body: { status: ReportStatus; resolution?: string }) =>
    request<ProblemReport>(`/api/v1/admin/problem-reports/${id}/resolution`, {
      method: "POST",
      auth: true,
      body,
    }),
};
