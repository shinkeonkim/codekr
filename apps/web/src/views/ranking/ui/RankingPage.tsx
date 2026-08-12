"use client";

import { rankingApi } from "@/entities/ranking";
import type { RankingEntry, RankingOptions } from "@/entities/ranking";
import { UserLink } from "@/entities/user";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Alert, Card, EmptyState, Pagination } from "@/shared/ui";
import { useEffect, useState } from "react";

const PAGE_SIZE = 50;

/**
 * 랭킹 (#57, #85, #58).
 *
 * **고를 수 있는 축을 서버에서 받는다.** 지표나 기간이 늘어날 때 이 화면을 같이 고쳐야
 * 하는 구조를 만들지 않는다.
 */
export function RankingPage() {
  const [options, setOptions] = useState<RankingOptions | null>(null);
  const [metric, setMetric] = useState("SCORE");
  const [period, setPeriod] = useState("ALL_TIME");
  const [entries, setEntries] = useState<RankingEntry[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    rankingApi.options().then(setOptions).catch(() => setOptions(null));
  }, []);

  useEffect(() => {
    rankingApi
      .list({ metric, period, page, size: PAGE_SIZE })
      .then((result) => {
        setEntries(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
        setError(null);
      })
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "랭킹을 불러오지 못했습니다."),
      );
  }, [metric, period, page]);

  const metricInfo = options?.metrics.find((it) => it.value === metric);

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">랭킹</h1>
        {/* 무엇을 재는 숫자인지 모르면 순위는 그냥 줄 세우기다. */}
        <p className="mt-1 text-xs text-ink-muted">{metricInfo?.description ?? " "}</p>
      </header>

      <div className="flex flex-wrap gap-2">
        <Choices
          options={options?.periods ?? []}
          value={period}
          onChange={(next) => {
            setPeriod(next);
            setPage(0);
          }}
        />
        <Choices
          options={options?.metrics ?? []}
          value={metric}
          onChange={(next) => {
            setMetric(next);
            setPage(0);
          }}
        />
      </div>

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
        onChange={setPage}
      />
    </div>
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

function Choices({
  options,
  value,
  onChange,
}: {
  options: { value: string; label: string }[];
  value: string;
  onChange: (next: string) => void;
}) {
  if (options.length === 0) return null;

  return (
    <div className="inline-flex rounded-lg border border-border p-0.5">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          aria-pressed={option.value === value}
          className={`rounded-md px-3 py-1 text-xs font-medium transition ${
            option.value === value
              ? "bg-brand text-brand-ink"
              : "text-ink-muted hover:text-ink"
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
