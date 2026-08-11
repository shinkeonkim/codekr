"use client";

import { contestApi } from "@/entities/contest";
import type { ContestSummary } from "@/entities/contest";
import { formatDateTime } from "@/shared/lib";
import { Card, EmptyState, Pagination } from "@/shared/ui";
import { PhaseBadge } from "@/entities/contest";
import Link from "next/link";
import { useEffect, useState } from "react";

const PAGE_SIZE = 20;

export function ContestListPage() {
  const [contests, setContests] = useState<ContestSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    contestApi
      .list(page, PAGE_SIZE)
      .then((result) => {
        setContests(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      .catch(() => setContests([]))
      .finally(() => setLoading(false));
  }, [page]);

  if (loading) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">대회</h1>
      </header>

      {contests.length === 0 ? (
        <EmptyState title="아직 열린 대회가 없습니다." />
      ) : (
        <div className="space-y-2">
          {contests.map((contest) => (
            <ContestCard key={contest.slug} contest={contest} />
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={setPage} />
    </div>
  );
}

function ContestCard({ contest }: { contest: ContestSummary }) {
  return (
    <Link href={`/contests/${encodeURIComponent(contest.slug)}`} className="block">
      <Card className="flex flex-wrap items-center gap-3 p-5 transition hover:border-brand/50">
        <PhaseBadge contest={contest} />
        <span className="flex-1 truncate font-medium text-ink">{contest.title}</span>
        <span className="text-xs text-ink-muted">참가 {contest.participantCount}명</span>
        <span className="w-full text-xs text-ink-muted sm:w-auto">
          {formatDateTime(contest.startsAt)} — {formatDateTime(contest.endsAt)}
        </span>
      </Card>
    </Link>
  );
}
