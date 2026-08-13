"use client";

import { PhaseBadge, contestApi } from "@/entities/contest";
import type { ContestDetail, Scoreboard as ScoreboardData } from "@/entities/contest";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Alert, Button, Card, CardTitle, EmptyState, useToast } from "@/shared/ui";
import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { ContestBoard } from "./ContestBoard";
import { Scoreboard } from "./Scoreboard";

/**
 * 순위표를 다시 받는 주기.
 *
 * 서버가 같은 주기로 캐시한다 — 더 자주 물어도 같은 답이 온다.
 * 순위표는 초 단위 정확도가 필요한 화면이 아니다.
 */
const POLL_INTERVAL_MS = 10_000;

export function ContestDetailPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  return <DetailView slug={decodeURIComponent(slug)} />;
}

function DetailView({ slug }: { slug: string }) {
  const toast = useToast();
  const { user, isAdmin } = useAuth();
  const [contest, setContest] = useState<ContestDetail | null>(null);
  const [scoreboard, setScoreboard] = useState<ScoreboardData | null>(null);
  const [actual, setActual] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    contestApi
      .detail(slug)
      .then(setContest)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "대회를 불러오지 못했습니다."),
      );
  }, [slug]);

  useEffect(load, [load]);

  useEffect(() => {
    let cancelled = false;
    const fetchScoreboard = () => {
      contestApi
        .scoreboard(slug, actual)
        .then((next) => {
          if (!cancelled) setScoreboard(next);
        })
        .catch(() => undefined);
    };
    fetchScoreboard();
    const timer = setInterval(fetchScoreboard, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [slug, actual]);

  const register = async () => {
    try {
      await contestApi.register(slug);
      toast.success("참가 등록했습니다.");
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "참가 등록하지 못했습니다.");
    }
  };

  if (error) return <EmptyState title={error} />;
  if (!contest) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  const { summary } = contest;

  return (
    <div className="space-y-5">
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold text-ink">{summary.title}</h1>
          <PhaseBadge contest={summary} />
        </div>
        <p className="text-xs text-ink-muted">
          {formatDateTime(summary.startsAt)} — {formatDateTime(summary.endsAt)} · 참가{" "}
          {summary.participantCount}명
          {contest.freezeAt ? ` · 동결 ${formatDateTime(contest.freezeAt)}` : " · 동결 없음"}
        </p>
      </header>

      {contest.description ? (
        <Card className="whitespace-pre-wrap p-5 text-sm text-ink">{contest.description}</Card>
      ) : null}

      {contest.canRegister ? (
        <Button onClick={register}>참가 등록</Button>
      ) : !user ? (
        <Alert>참가하려면 로그인이 필요합니다.</Alert>
      ) : contest.registered ? (
        <Alert>참가 등록되어 있습니다.</Alert>
      ) : null}

      <ProblemList contest={contest} />

      <ContestBoard slug={slug} registered={contest.registered} />

      {scoreboard ? (
        <Scoreboard
          data={scoreboard}
          actual={actual}
          canSeeActual={isAdmin}
          onToggleActual={setActual}
        />
      ) : null}
    </div>
  );
}

function ProblemList({ contest }: { contest: ContestDetail }) {
  if (contest.problems.length === 0) {
    // 왜 비어 있는지 말해 준다. 그냥 비어 있으면 고장으로 보인다.
    const reason =
      contest.summary.phase === "SCHEDULED"
        ? "대회가 시작하면 문제가 공개됩니다."
        : contest.registered
          ? "배정된 문제가 없습니다."
          : "참가 등록한 사람만 문제를 볼 수 있습니다.";
    return <EmptyState title={reason} />;
  }

  return (
    <section className="space-y-2">
      <CardTitle>문제</CardTitle>
      <Card className="divide-y divide-border p-0">
        {contest.problems.map((problem) => (
          <Link
            key={problem.slug}
            href={`/problems/${problem.id}`}
            className="flex items-center gap-3 px-5 py-3 transition hover:bg-surface-muted"
          >
            <span className="w-6 font-semibold text-ink-muted">{problem.label}</span>
            <span className={`flex-1 truncate text-ink ${problem.excluded ? "line-through" : ""}`}>
              {problem.title}
            </span>
            {problem.excluded ? (
              <span className="text-xs text-danger">대회에서 제외됨</span>
            ) : null}
            <span className="text-xs tabular-nums text-ink-muted">{problem.score}점</span>
          </Link>
        ))}
      </Card>
    </section>
  );
}
