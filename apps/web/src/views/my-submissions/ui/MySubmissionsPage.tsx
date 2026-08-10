"use client";

import { ActivityGraph, activityApi } from "@/entities/activity";
import type { ActivityResponse } from "@/entities/activity";
import { STATUS_LABELS, VERDICT_LABELS, submissionApi, verdictTone } from "@/entities/submission";
import type { SubmissionSummary } from "@/entities/submission";
import { RequireAuth } from "@/features/auth";
import type { Page } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Badge, Card, EmptyState } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

export function MySubmissionsPage() {
  return (
    <RequireAuth>
      <SubmissionList />
    </RequireAuth>
  );
}

function SubmissionList() {
  const [result, setResult] = useState<Page<SubmissionSummary> | null>(null);
  const [activity, setActivity] = useState<ActivityResponse | null>(null);

  useEffect(() => {
    submissionApi
      .mine({ page: 0, size: 30 })
      .then(setResult)
      .catch(() => setResult({ content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 }));
    activityApi.mine().then(setActivity).catch(() => setActivity(null));
  }, []);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">내 제출</h1>

      {activity ? <ActivityGraph activity={activity} /> : null}

      {result && result.content.length === 0 ? (
        <EmptyState title="아직 제출한 코드가 없습니다." description="문제를 골라 풀어 보세요." />
      ) : null}
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
