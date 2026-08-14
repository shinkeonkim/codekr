"use client";

import { contestApi } from "@/entities/contest";
import type { ContestDetail } from "@/entities/contest";
import { useProblem } from "@/entities/problem";
import type { Runtime } from "@/entities/problem";
import { RequireAuth } from "@/features/auth";
import { Alert, EmptyState } from "@/shared/ui";
import { ProblemHeader } from "@/widgets/problem-tabs";
import { SolveWorkspace } from "@/widgets/solve-workspace";
import Link from "next/link";
import { use, useEffect, useState } from "react";

/**
 * 대회 안에서 문제를 푼다 (#62, #541).
 *
 * ## 왜 별도 주소인가
 *
 * 전에는 대회 상세가 문제를 **평소 문제 주소**(`/problems/{id}`)로 이었다. 그리로 가면
 * 제출도 평소 경로로 가고, 그것은 **대회 큐가 아니라 평소 큐**로 들어간다 — 순위표에
 * 잡히지 않는다. 서버가 경로를 나눈 이유(#62)가 화면에서 지켜지지 않고 있었다.
 *
 * 그래서 대회 맥락을 주소가 들고 있게 한다. 여기서 낸 것은 반드시 대회로 간다.
 *
 * ## 화면은 평소 것을 그대로 쓴다
 *
 * `SolveWorkspace` 를 복사하지 않는다 — 복사하면 한쪽만 고치는 일이 생기고, 그것은
 * 대회에서만 나는 버그가 된다.
 */
export function ContestSolvePage({
  params,
}: {
  params: Promise<{ slug: string; problemSlug: string }>;
}) {
  return (
    <RequireAuth>
      <ContestSolveView params={params} />
    </RequireAuth>
  );
}

function ContestSolveView({
  params,
}: {
  params: Promise<{ slug: string; problemSlug: string }>;
}) {
  const { slug, problemSlug } = use(params);
  const { problem, error } = useProblem(problemSlug);
  const [contest, setContest] = useState<ContestDetail | null>(null);
  const [runtime, setRuntime] = useState<Runtime | undefined>();

  useEffect(() => {
    contestApi
      .detail(slug)
      .then(setContest)
      .catch(() => setContest(null));
  }, [slug]);

  if (error) return <EmptyState title={error} />;
  if (!problem || !contest) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  const inContest = contest.problems.some((each) => each.slug === problemSlug);

  return (
    <div className="space-y-5">
      <nav className="text-sm text-ink-muted">
        <Link href={`/contests/${encodeURIComponent(slug)}`} className="hover:underline">
          ← {contest.summary.title}
        </Link>
      </nav>

      {/*
        참가자가 아니면 서버가 제출을 막는다. 그 전에 화면이 말해 주지 않으면
        코드를 다 쓴 뒤에야 알게 된다.
      */}
      {!contest.registered ? (
        <Alert tone="warn">
          이 대회에 참가하지 않았습니다. 제출하려면 대회 페이지에서 먼저 신청하십시오.
        </Alert>
      ) : null}

      {!inContest ? (
        <Alert tone="warn">이 대회의 문제가 아닙니다. 제출하면 거절됩니다.</Alert>
      ) : null}

      <ProblemHeader problem={problem} runtime={runtime} />
      {/* 대회를 넘긴다 — 여기서 낸 것은 반드시 대회 큐로 간다 (#62). */}
      <SolveWorkspace problem={problem} onRuntimeChange={setRuntime} contest={{ slug }} />
    </div>
  );
}
