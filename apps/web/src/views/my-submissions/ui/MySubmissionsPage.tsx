"use client";

import { SubmissionResult, submissionApi } from "@/entities/submission";
import type { SubmissionSummary } from "@/entities/submission";
import { RequireAuth, useAuth } from "@/features/auth";
import Link from "next/link";
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
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<SubmissionSummary> | null>(null);

  useEffect(() => {
    submissionApi
      .mine({ page, size: 20 })
      .then(setResult)
      .catch(() => setResult({ content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 }));
  }, [page]);


  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">내 제출</h1>

      {/*
        활동 그래프는 프로필에만 둔다 (#117). 두 곳에 두면 같은 것을 두 곳에서 고치게 되고,
        본인 프로필과 이 화면에 똑같은 그래프가 두 번 나온다.
      */}
      {user ? (
        <p className="text-sm text-ink-muted">
          활동 기록은{" "}
          <Link href={`/users/${encodeURIComponent(user.nickname)}`} className="text-brand hover:underline">
            내 프로필
          </Link>
          에서 볼 수 있습니다.
        </p>
      ) : null}

      {result && result.content.length === 0 ? (
        <EmptyState title="아직 제출한 코드가 없습니다." description="문제를 골라 풀어 보세요." />
      ) : null}
      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(submission) => submission.id}
            columns={[
              {
                key: "problem",
                header: "문제",
                // 문제 열은 문제로 간다 (#197).
                href: (submission) => `/problems/${submission.problemSlug}`,
                render: (submission) => submission.problemTitle,
              },
              {
                key: "runtime",
                header: "언어",
                hideBelow: "sm",
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
                hideBelow: "sm",
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
                hideBelow: "sm",
                align: "right",
                render: (submission) => (
                  <span className="whitespace-nowrap text-xs text-ink-muted">
                    {formatDateTime(submission.createdAt)}
                  </span>
                ),
              },
              {
                key: "detail",
                header: "제출",
                align: "right",
                // 내 제출이라 코드는 언제나 보인다 — 따로 표시할 것이 없다.
                href: (submission) => `/submissions/${submission.id}`,
                render: (submission) => (
                  <span className="whitespace-nowrap text-xs">#{submission.id}</span>
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
