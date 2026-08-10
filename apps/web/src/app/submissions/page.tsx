"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { Badge, Card, EmptyState } from "@/components/ui";
import { api } from "@/lib/api";
import { STATUS_LABELS, VERDICT_LABELS, formatDateTime, verdictTone } from "@/lib/labels";
import type { Page, SubmissionSummary } from "@/lib/types";

export default function SubmissionListPage() {
  return (
    <RequireAuth>
      <SubmissionList />
    </RequireAuth>
  );
}

function SubmissionList() {
  const [result, setResult] = useState<Page<SubmissionSummary> | null>(null);

  useEffect(() => {
    api
      .submissions({ page: 0, size: 30 })
      .then(setResult)
      .catch(() => setResult({ content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 }));
  }, []);

  if (result && result.content.length === 0) {
    return <EmptyState title="아직 제출한 코드가 없습니다." description="문제를 골라 풀어 보세요." />;
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">내 제출</h1>
      <div className="space-y-2">
        {result?.content.map((submission) => (
          <Link key={submission.id} href={`/submissions/${submission.id}`} className="block">
            <Card className="flex items-center gap-4 px-5 py-3 transition hover:border-brand">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-ink">{submission.problemTitle}</p>
                <p className="mt-0.5 text-xs text-ink-muted">
                  {submission.runtimeId} · {formatDateTime(submission.createdAt)}
                </p>
              </div>
              {submission.verdict ? (
                <Badge tone={verdictTone(submission.verdict)}>
                  {VERDICT_LABELS[submission.verdict]} · {submission.passedCount}/{submission.totalCount}
                </Badge>
              ) : (
                <Badge>{STATUS_LABELS[submission.status]}</Badge>
              )}
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
