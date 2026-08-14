"use client";

import { problemReportApi } from "@/entities/problem-report";
import type { ProblemReport, ReportStatus } from "@/entities/problem-report";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Alert, Button, EmptyState, Pagination, useToast } from "@/shared/ui";
import { useCallback, useEffect, useState } from "react";
import { ReportCard } from "./ReportCard";

const FILTERS: { value: ReportStatus | ""; label: string }[] = [
  { value: "OPEN", label: "아직 안 본 것" },
  { value: "ACCEPTED", label: "고쳤음" },
  { value: "REJECTED", label: "문제 없음" },
  { value: "", label: "전부" },
];

/**
 * 들어온 문제 오류 신고를 처리한다 (#478, #548).
 *
 * **신고 폼과 이 화면은 함께 나가야 한다.** 폼만 있으면 신고가 아무도 안 보는 곳에
 * 쌓이고 사용자는 알렸다고 믿는다. 이 화면만 있으면 영원히 비어 있다.
 *
 * 기본 거르개가 `OPEN` 인 이유: **처리할 것이 있는지가 이 화면을 여는 이유**다.
 * 전부를 먼저 보이면 처리된 것 사이에서 새것을 찾게 된다.
 */
export function AdminProblemReportsPage() {
  const toast = useToast();
  const [status, setStatus] = useState<ReportStatus | "">("OPEN");
  const [result, setResult] = useState<Page<ProblemReport> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    problemReportApi
      .list({ status: status || undefined, page, size: 20 })
      .then(setResult)
      .catch(() => setError("신고를 불러오지 못했습니다."));
  }, [status, page]);

  useEffect(load, [load]);

  const resolve = async (id: number, next: ReportStatus, resolution: string) => {
    try {
      await problemReportApi.resolve(id, { status: next, resolution: resolution || undefined });
      toast.success("처리했습니다.");
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "처리하지 못했습니다.");
    }
  };

  const select = (next: ReportStatus | "") => {
    setPage(0);
    setStatus(next);
  };

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-2xl font-bold text-ink">문제 오류 신고</h1>
        <p className="mt-1 text-sm text-ink-muted">
          사용자가 알린 지문·테스트케이스·정답의 문제입니다. 질문(#139)과 다릅니다 —
          이건 어드민만 봅니다.
        </p>
      </header>

      <nav className="flex flex-wrap gap-1 text-sm">
        {FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => select(filter.value)}
            aria-current={status === filter.value}
            className="rounded-full px-3 py-1 text-ink-muted transition hover:text-ink aria-[current=true]:bg-surface-muted aria-[current=true]:font-medium aria-[current=true]:text-ink"
          >
            {filter.label}
          </button>
        ))}
      </nav>

      {error ? <Alert>{error}</Alert> : null}

      {result && result.content.length === 0 ? (
        <EmptyState
          title={status === "OPEN" ? "처리할 신고가 없습니다." : "해당하는 신고가 없습니다."}
        />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((report) => (
          <ReportCard key={report.id} report={report} onResolve={resolve} />
        ))}
      </div>

      {result ? (
        <Pagination
          page={result.page}
          totalPages={result.totalPages}
          totalElements={result.totalElements}
          onChange={setPage}
        />
      ) : null}
    </div>
  );
}
