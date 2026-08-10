"use client";

import { CATEGORY_LABELS, ProblemStatsCell, TIER_LABELS, TierBadge, problemApi } from "@/entities/problem";
import type { ProblemCategory, ProblemSummary } from "@/entities/problem";
import type { Page } from "@/shared/api";
import { EmptyState, Input, Pagination, Select, Table } from "@/shared/ui";
import { useEffect, useState } from "react";

export function ProblemListPage() {
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState("");
  const [tier, setTier] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<ProblemSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 입력할 때마다 요청하지 않도록 잠깐 기다렸다가 조회한다.
  useEffect(() => {
    const timer = setTimeout(() => {
      problemApi
        .list({ q: keyword, category, tier, page, size: 20 })
        .then((data) => {
          setResult(data);
          setError(null);
        })
        .catch(() => setError("문제 목록을 불러오지 못했습니다."));
    }, 200);

    return () => clearTimeout(timer);
  }, [keyword, category, tier, page]);

  const resetPageAnd = (apply: () => void) => {
    setPage(0);
    apply();
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">문제</h1>
        <p className="mt-1 text-sm text-ink-muted">유형과 난이도를 골라 원하는 문제를 찾아보세요.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-[1fr_auto_auto]">
        <Input
          placeholder="문제 제목 검색"
          aria-label="문제 제목 검색"
          value={keyword}
          onChange={(event) => resetPageAnd(() => setKeyword(event.target.value))}
        />
        <Select
          aria-label="유형"
          value={category}
          onChange={(event) => resetPageAnd(() => setCategory(event.target.value))}
        >
          <option value="">전체 유형</option>
          {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select
          aria-label="티어"
          value={tier}
          onChange={(event) => resetPageAnd(() => setTier(event.target.value))}
        >
          <option value="">전체 티어</option>
          {Object.entries(TIER_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </div>

      {error ? <EmptyState title={error} /> : null}

      {result && result.content.length === 0 ? (
        <EmptyState title="조건에 맞는 문제가 없습니다." description="검색어나 필터를 바꿔 보세요." />
      ) : null}

      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(problem) => problem.id}
            href={(problem) => `/problems/${problem.slug}`}
            columns={[
              { key: "title", header: "문제", render: (problem) => problem.title },
              {
                key: "category",
                header: "유형",
                hideOnMobile: true,
                render: (problem) => (
                  <span className="text-ink-muted">
                    {CATEGORY_LABELS[problem.category as ProblemCategory]}
                  </span>
                ),
              },
              {
                key: "limits",
                header: "제한",
                hideOnMobile: true,
                render: (problem) => (
                  <span className="whitespace-nowrap text-xs text-ink-muted">
                    {problem.timeLimitMs}ms · {problem.memoryLimitMb}MB
                  </span>
                ),
              },
              {
                key: "stats",
                header: "맞은 사람 · 정답률",
                hideOnMobile: true,
                align: "right",
                render: (problem) => <ProblemStatsCell stats={problem.stats} />,
              },
              {
                key: "difficulty",
                header: "난이도",
                align: "right",
                render: (problem) => (
                  <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
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
