"use client";

import { rankingApi } from "@/entities/ranking";
import type { RankingEntry } from "@/entities/ranking";
import { UserLink } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Alert, Card, EmptyState, Pagination } from "@/shared/ui";
import { useEffect, useState } from "react";

const PAGE_SIZE = 50;

/**
 * 사람 순위표 (#57, #85).
 *
 * [affiliationId] 가 있으면 **모집단만 좁는다** (#399) — 등수는 그 안에서 1위부터 다시
 * 매겨진다.
 */
export function UserRankingList({
  metric,
  period,
  affiliationId,
  page,
  onPageChange,
}: {
  metric: string;
  period: string;
  affiliationId: number | undefined;
  page: number;
  onPageChange: (next: number) => void;
}) {
  const [entries, setEntries] = useState<RankingEntry[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    rankingApi
      .list({ metric, period, page, size: PAGE_SIZE, affiliationId })
      .then((result) => {
        setEntries(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
        setError(null);
      })
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "랭킹을 불러오지 못했습니다."),
      );
  }, [metric, period, page, affiliationId]);

  return (
    <>
      {error ? <Alert>{error}</Alert> : null}

      {entries.length === 0 && !error ? (
        <EmptyState mascot="award" title="아직 랭킹에 오른 사람이 없습니다." />
      ) : (
        <Card className="divide-y divide-border p-0">
          {entries.map((entry) => (
            <RankingRow key={entry.nickname} entry={entry} metric={metric} />
          ))}
        </Card>
      )}

      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onChange={onPageChange}
      />
    </>
  );
}

function RankingRow({ entry, metric }: { entry: RankingEntry; metric: string }) {
  return (
    <div className="flex items-center gap-4 px-5 py-3">
      <span className="w-10 shrink-0 text-sm font-semibold tabular-nums text-ink-muted">
        {entry.rank}
      </span>
      <span className="flex-1 truncate">
        <UserLink nickname={entry.nickname} />
      </span>
      {/* 고른 지표를 굵게 둔다. 나머지도 함께 보여야 순위가 어떻게 갈렸는지 읽힌다. */}
      <span
        className={`w-24 text-right text-sm tabular-nums ${
          metric === "SCORE" ? "font-semibold text-ink" : "text-ink-muted"
        }`}
      >
        {entry.score.toLocaleString()}점
      </span>
      <span
        className={`hidden w-24 text-right text-sm tabular-nums sm:inline ${
          metric === "SOLVED_COUNT" ? "font-semibold text-ink" : "text-ink-muted"
        }`}
      >
        {entry.solvedCount}문제
      </span>
      <span className="hidden w-32 text-right text-xs text-ink-muted lg:inline">
        {entry.lastSolvedAt ? formatDateTime(entry.lastSolvedAt) : "—"}
      </span>
    </div>
  );
}
