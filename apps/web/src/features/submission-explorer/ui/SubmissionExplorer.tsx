"use client";

import { problemApi } from "@/entities/problem";
import type { Runtime } from "@/entities/problem";
import { SubmissionResult, submissionApi } from "@/entities/submission";
import { UserLink } from "@/entities/user";
import type { SubmissionSummary } from "@/entities/submission";
import type { Page } from "@/shared/api";
import { formatDateTime, formatMemory } from "@/shared/lib";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { EmptyState, Pagination, Table } from "@/shared/ui";
import { FILTER_KEYS, activeChips, hasActiveFilters } from "../model/filters";
import type { FilterKey, Filters } from "../model/filters";
import { FilterChips } from "./FilterChips";
import { FilterPanel } from "./FilterPanel";


interface Props {
  /** 문제 상세 안에서 쓸 때는 그 문제로 범위를 고정한다. */
  fixedProblemSlug?: string;
  /** 프로필 안에서 쓸 때는 그 사람으로 범위를 고정한다 (#83). */
  fixedNickname?: string;
  emptyMessage?: string;
}

export function SubmissionExplorer({ fixedProblemSlug, fixedNickname, emptyMessage }: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [runtimes, setRuntimes] = useState<Runtime[]>([]);
  const [result, setResult] = useState<Page<SubmissionSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const filters: Filters = Object.fromEntries(
    FILTER_KEYS.map((key) => [key, searchParams.get(key) ?? ""]).filter(([, value]) => value !== ""),
  );

  const setFilter = useCallback(
    (key: FilterKey, value: string) => {
      const next = new URLSearchParams(searchParams.toString());
      if (value) next.set(key, value);
      else next.delete(key);
      // 조건이 바뀌면 첫 페이지부터 다시 본다.
      if (key !== "page") next.delete("page");
      router.replace(next.size > 0 ? `${pathname}?${next}` : pathname, { scroll: false });
    },
    [pathname, router, searchParams],
  );

  useEffect(() => {
    problemApi.runtimes().then(setRuntimes).catch(() => setRuntimes([]));
  }, []);

  useEffect(() => {
    submissionApi
      .explore({
        ...filters,
        problemSlug: fixedProblemSlug ?? filters.problemSlug,
        nickname: fixedNickname ?? filters.nickname,
        size: 20,
      })
      .then((response) => {
        setResult(response);
        setError(null);
      })
      .catch(() => setError("제출 내역을 불러오지 못했습니다."));
    // searchParams 문자열이 바뀔 때만 다시 조회한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams.toString(), fixedProblemSlug, fixedNickname]);

  // 범위가 고정된 화면(문제 상세·프로필)에서는 그 필터를 그리지도, 칩으로 보이지도 않는다.
  const hidden: FilterKey[] = [
    ...(fixedProblemSlug ? (["problemSlug"] as FilterKey[]) : []),
    ...(fixedNickname ? (["nickname"] as FilterKey[]) : []),
  ];
  const hasFilters = hasActiveFilters(filters, hidden);

  const clearFilters = () => {
    const next = new URLSearchParams(searchParams.toString());
    activeChips(filters, hidden).forEach((key) => next.delete(key));
    next.delete("page");
    router.replace(next.size > 0 ? `${pathname}?${next}` : pathname, { scroll: false });
  };

  return (
    <div className="space-y-4">
      <FilterChips
        filters={filters}
        hidden={hidden}
        runtimes={runtimes}
        onRemove={(key) => setFilter(key, "")}
        onClear={clearFilters}
      />

      <FilterPanel filters={filters} hidden={hidden} runtimes={runtimes} onChange={setFilter} />

      {error ? <EmptyState title={error} /> : null}

      {/*
        빈 화면은 두 가지다 (#76). **필터를 안 걸었는데 비었다**는 것과
        **조건에 맞는 것이 없다**는 것은 다른 말이고, 사용자가 할 일도 다르다.
      */}
      {result && result.content.length === 0 ? (
        hasFilters ? (
          <EmptyState
            title="조건에 맞는 제출이 없습니다."
            description="위의 필터를 지우거나 바꿔 보세요."
          />
        ) : (
          <EmptyState title={emptyMessage ?? "아직 제출이 없습니다."} />
        )
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
                key: "nickname",
                header: "제출자",
                render: (submission) => (
                  <UserLink nickname={submission.nickname} className="text-ink-muted" />
                ),
              },
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
            onChange={(page) => setFilter("page", String(page))}
          />
        </>
      ) : null}
    </div>
  );
}
