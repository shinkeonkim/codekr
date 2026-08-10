"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { TierBadge } from "@/components/TierBadge";
import { Card, EmptyState, Input, Select } from "@/components/ui";
import { api } from "@/lib/api";
import { CATEGORY_LABELS, TIER_LABELS } from "@/lib/labels";
import type { Page, ProblemCategory, ProblemSummary } from "@/lib/types";

export default function ProblemListPage() {
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState("");
  const [tier, setTier] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<ProblemSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 입력할 때마다 요청하지 않도록 잠깐 기다렸다가 조회한다.
  useEffect(() => {
    const timer = setTimeout(() => {
      api
        .problems({ q: keyword, category, tier, page, size: 20 })
        .then((response) => {
          setResult(response);
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
          value={keyword}
          onChange={(event) => resetPageAnd(() => setKeyword(event.target.value))}
        />
        <Select value={category} onChange={(event) => resetPageAnd(() => setCategory(event.target.value))}>
          <option value="">전체 유형</option>
          {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select value={tier} onChange={(event) => resetPageAnd(() => setTier(event.target.value))}>
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

      <div className="space-y-2">
        {result?.content.map((problem) => (
          <Link key={problem.id} href={`/problems/${problem.slug}`} className="block">
            <Card className="flex items-center gap-4 px-5 py-4 transition hover:border-brand">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-ink">{problem.title}</p>
                <p className="mt-1 text-xs text-ink-muted">
                  {CATEGORY_LABELS[problem.category as ProblemCategory]} · 시간 {problem.timeLimitMs}ms · 메모리{" "}
                  {problem.memoryLimitMb}MB
                </p>
              </div>
              <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
            </Card>
          </Link>
        ))}
      </div>

      {result && result.totalPages > 1 ? (
        <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
      ) : null}
    </div>
  );
}

function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  return (
    <div className="flex items-center justify-center gap-2 text-sm">
      <button
        className="rounded-lg border border-border px-3 py-1.5 disabled:opacity-40"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        이전
      </button>
      <span className="text-ink-muted">
        {page + 1} / {totalPages}
      </span>
      <button
        className="rounded-lg border border-border px-3 py-1.5 disabled:opacity-40"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        다음
      </button>
    </div>
  );
}
