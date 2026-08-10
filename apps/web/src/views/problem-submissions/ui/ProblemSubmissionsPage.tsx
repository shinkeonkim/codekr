"use client";

import { useProblem } from "@/entities/problem";
import { RequireAuth } from "@/features/auth";
import { SubmissionExplorer } from "@/features/submission-explorer";
import { EmptyState } from "@/shared/ui";
import { ProblemHeader } from "@/widgets/problem-tabs";
import { Suspense, use } from "react";

export function ProblemSubmissionsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);

  return (
    <RequireAuth>
      <ProblemSubmissions slug={slug} />
    </RequireAuth>
  );
}

function ProblemSubmissions({ slug }: { slug: string }) {
  const { problem, error } = useProblem(slug);

  if (error) return <EmptyState title={error} />;
  if (!problem) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <ProblemHeader problem={problem} />
      {/* 이 문제로 범위를 고정하고 나머지 필터는 전체 목록(#34)의 것을 그대로 쓴다. */}
      <Suspense fallback={<p className="py-8 text-center text-sm text-ink-muted">불러오는 중…</p>}>
        <SubmissionExplorer
          fixedProblemSlug={slug}
          emptyMessage="이 문제에 대한 제출이 아직 없습니다."
        />
      </Suspense>
    </div>
  );
}
