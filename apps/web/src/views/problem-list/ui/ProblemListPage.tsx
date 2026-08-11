"use client";

import { CATEGORY_LABELS, PROBLEM_SORTS, TIER_LABELS, problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import type { Page } from "@/shared/api";
import { EmptyState, Field, Input, Pagination, Select, Table } from "@/shared/ui";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { PROBLEM_COLUMNS } from "./problemColumns";

/** URL 에 담는 목록 상태. 새로고침·뒤로가기·링크 공유 후에도 같은 목록이 나와야 한다 (#76 과 같은 규칙). */
const KEYS = ["q", "category", "tier", "sort", "page"] as const;
type Key = (typeof KEYS)[number];

export function ProblemListPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [result, setResult] = useState<Page<ProblemSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const value = useCallback((key: Key) => searchParams.get(key) ?? "", [searchParams]);
  const keyword = value("q");

  const setParam = useCallback(
    (key: Key, next: string) => {
      const params = new URLSearchParams(searchParams.toString());
      if (next) params.set(key, next);
      else params.delete(key);
      // 조건이 바뀌면 첫 페이지부터 다시 본다.
      if (key !== "page") params.delete("page");
      router.replace(params.size > 0 ? `${pathname}?${params}` : pathname, { scroll: false });
    },
    [pathname, router, searchParams],
  );

  // 입력할 때마다 요청하지 않도록 잠깐 기다렸다가 조회한다.
  useEffect(() => {
    const timer = setTimeout(() => {
      problemApi
        .list({
          q: keyword,
          category: value("category"),
          tier: value("tier"),
          sort: value("sort") || "LATEST",
          page: value("page") || 0,
          size: 20,
        })
        .then((data) => {
          setResult(data);
          setError(null);
        })
        .catch(() => setError("문제 목록을 불러오지 못했습니다."));
    }, 200);

    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams.toString()]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">문제</h1>
        <p className="mt-1 text-sm text-ink-muted">유형과 난이도를 골라 원하는 문제를 찾아보세요.</p>
      </div>

      {/* 라벨을 붙인다 — placeholder 는 값을 넣으면 사라진다 (#76 과 같은 규칙). */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="검색">
          <Input
            placeholder="문제 제목"
            value={keyword}
            onChange={(event) => setParam("q", event.target.value)}
          />
        </Field>
        <Field label="유형">
          <Select value={value("category")} onChange={(event) => setParam("category", event.target.value)}>
            <option value="">전체</option>
            {Object.entries(CATEGORY_LABELS).map(([option, label]) => (
              <option key={option} value={option}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="티어">
          <Select value={value("tier")} onChange={(event) => setParam("tier", event.target.value)}>
            <option value="">전체</option>
            {Object.entries(TIER_LABELS).map(([option, label]) => (
              <option key={option} value={option}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        {/*
          표 헤더 클릭이 아니라 Select 인 이유: 좁은 화면에서는 열이 숨는데(hideBelow),
          숨은 열로는 정렬할 수 없다. #132 의 완료 조건이 "모바일에서도 바꿀 수 있다" 다.
        */}
        <Field label="정렬">
          <Select value={value("sort") || "LATEST"} onChange={(event) => setParam("sort", event.target.value)}>
            {PROBLEM_SORTS.map((sort) => (
              <option key={sort.value} value={sort.value}>
                {sort.label}
              </option>
            ))}
          </Select>
        </Field>
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
            columns={PROBLEM_COLUMNS}
          />
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onChange={(next) => setParam("page", String(next))}
          />
        </>
      ) : null}
    </div>
  );
}
