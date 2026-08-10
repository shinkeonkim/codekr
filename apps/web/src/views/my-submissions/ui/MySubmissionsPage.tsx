"use client";

import { ActivityGraph, activityApi } from "@/entities/activity";
import type { ActivityResponse } from "@/entities/activity";
import { SubmissionResult, submissionApi } from "@/entities/submission";
import type { SubmissionSummary } from "@/entities/submission";
import { RequireAuth } from "@/features/auth";
import type { Page } from "@/shared/api";
import { formatDateTime, formatMemory } from "@/shared/lib";
import { EmptyState, Pagination, Table } from "@/shared/ui";
import { useEffect, useState } from "react";

export function MySubmissionsPage() {
  return (
    <RequireAuth>
      <SubmissionList />
    </RequireAuth>
  );
}

function SubmissionList() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<SubmissionSummary> | null>(null);
  const [activity, setActivity] = useState<ActivityResponse | null>(null);
  const [year, setYear] = useState(() => new Date().getFullYear());

  useEffect(() => {
    submissionApi
      .mine({ page, size: 20 })
      .then(setResult)
      .catch(() => setResult({ content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 }));
  }, [page]);

  useEffect(() => {
    activityApi.mine({ year }).then(setActivity).catch(() => setActivity(null));
  }, [year]);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">내 제출</h1>

      {activity ? <ActivityGraph activity={activity} year={year} onYearChange={setYear} /> : null}

      {result && result.content.length === 0 ? (
        <EmptyState title="아직 제출한 코드가 없습니다." description="문제를 골라 풀어 보세요." />
      ) : null}
      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(submission) => submission.id}
            href={(submission) => `/submissions/${submission.id}`}
            columns={[
              { key: "problem", header: "문제", render: (submission) => submission.problemTitle },
              {
                key: "runtime",
                header: "언어",
                hideOnMobile: true,
                render: (submission) => (
                  <span className="whitespace-nowrap text-xs text-ink-muted">{submission.runtimeId}</span>
                ),
              },
              {
                key: "result",
                header: "결과",
                render: (submission) => <SubmissionResult submission={submission} />,
              },
              {
                key: "cost",
                header: "시간 · 메모리",
                hideOnMobile: true,
                align: "right",
                render: (submission) =>
                  submission.status === "COMPLETED" ? (
                    <span className="whitespace-nowrap text-xs text-ink-muted">
                      {submission.maxRuntimeMs}ms · {formatMemory(submission.maxMemoryKb)}
                    </span>
                  ) : (
                    <span className="text-xs text-ink-muted">-</span>
                  ),
              },
              {
                key: "createdAt",
                header: "제출 시각",
                hideOnMobile: true,
                align: "right",
                render: (submission) => (
                  <span className="whitespace-nowrap text-xs text-ink-muted">
                    {formatDateTime(submission.createdAt)}
                  </span>
                ),
              },
            ]}
          />
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onChange={setPage}
          />
        </>
      ) : null}
    </div>
  );
}
