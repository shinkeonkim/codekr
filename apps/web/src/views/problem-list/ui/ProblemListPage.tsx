"use client";

import { CATEGORY_LABELS, SELECTABLE_KINDS, PROBLEM_SORTS, TIER_LABELS, problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import type { Page } from "@/shared/api";
import { TagFilter } from "@/features/tag-filter";
import { EmptyState, Field, Input, Pagination, Select, Table } from "@/shared/ui";
import { useAuth } from "@/features/auth";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { PROBLEM_COLUMNS } from "./problemColumns";

/** URL 에 담는 목록 상태. 새로고침·뒤로가기·링크 공유 후에도 같은 목록이 나와야 한다 (#76 과 같은 규칙). */
const KEYS = [
  "q",
  "category",
  "tier",
  "sort",
  "page",
  "tag",
  // 접어 둔 필터 (#239). 주소에 담는 규칙은 #132 그대로다.
  "kind",
  "acceptanceFrom",
  "acceptanceTo",
  "solversFrom",
  "solversTo",
  "solved",
] as const;
type Key = (typeof KEYS)[number];

export function ProblemListPage() {
  const { user } = useAuth();
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

  /** 태그는 여러 개가 같은 이름으로 들어온다 (`?tag=dp&tag=graph`). */
  const tags = searchParams.getAll("tag");

  const setTags = useCallback(
    (next: string[]) => {
      const params = new URLSearchParams(searchParams.toString());
      params.delete("tag");
      next.forEach((slug) => params.append("tag", slug));
      // 조건이 바뀌면 첫 페이지부터 다시 본다.
      params.delete("page");
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
          tag: searchParams.getAll("tag"),
          sort: value("sort") || "LATEST",
          kind: value("kind"),
          acceptanceFrom: value("acceptanceFrom"),
          acceptanceTo: value("acceptanceTo"),
          solversFrom: value("solversFrom"),
          solversTo: value("solversTo"),
          // 비로그인에게는 이 칸이 아예 없다 — 서버도 조용히 무시한다.
          solved: user ? value("solved") : "",
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

      {/* 태그는 개수가 정해져 있지 않아 Select 에 담기지 않는다. 칩으로 둔다 (#232). */}
      <TagFilter selected={tags} onChange={setTags} />

      {/*
        **자주 쓰는 것만 밖에 두고 나머지는 접는다** (#76 이 제출 목록에서 정한 규칙).
        필터를 다 펼쳐 두면 목록보다 필터가 길어진다.
      */}
      <details className="rounded-card border border-border p-4">
        <summary className="cursor-pointer text-sm font-medium text-ink">필터 더보기</summary>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="채점 방식">
            <Select value={value("kind")} onChange={(event) => setParam("kind", event.target.value)}>
              <option value="">전체</option>
              {Object.entries(SELECTABLE_KINDS).map(([option, label]) => (
                <option key={option} value={option}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>
          {/*
            **로그인해야 뜻이 있다** (#239). 비로그인에게 보이면 눌러도 아무 일이
            일어나지 않는 칸이 된다 — 서버가 조용히 무시하기 때문이다.
          */}
          {user ? (
            <Field label="해결 여부">
              <Select value={value("solved")} onChange={(event) => setParam("solved", event.target.value)}>
                <option value="">전체</option>
                <option value="false">안 푼 것만</option>
                <option value="true">푼 것만</option>
              </Select>
            </Field>
          ) : null}
          <Field label="정답률 (%)">
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min={0}
                max={100}
                placeholder="0"
                value={value("acceptanceFrom")}
                onChange={(event) => setParam("acceptanceFrom", event.target.value)}
              />
              <span className="text-sm text-ink-muted">~</span>
              <Input
                type="number"
                min={0}
                max={100}
                placeholder="100"
                value={value("acceptanceTo")}
                onChange={(event) => setParam("acceptanceTo", event.target.value)}
              />
            </div>
          </Field>
          <Field label="푼 사람 수">
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min={0}
                placeholder="0"
                value={value("solversFrom")}
                onChange={(event) => setParam("solversFrom", event.target.value)}
              />
              <span className="text-sm text-ink-muted">~</span>
              <Input
                type="number"
                min={0}
                placeholder="제한 없음"
                value={value("solversTo")}
                onChange={(event) => setParam("solversTo", event.target.value)}
              />
            </div>
          </Field>
        </div>
        {/* 제출자가 없는 문제는 정답률이 없다 — 범위를 걸면 빠진다 (#205). */}
        <p className="mt-3 text-xs text-ink-muted">
          정답률 범위를 걸면 아직 아무도 제출하지 않은 문제는 목록에서 빠집니다.
        </p>
      </details>

      {error ? <EmptyState title={error} /> : null}

      {result && result.content.length === 0 ? (
        <EmptyState
          mascot="laptop"
          title="조건에 맞는 문제가 없습니다."
          description={
            tags.length > 1
              ? "고른 분류를 모두 만족하는 문제만 나옵니다. 하나씩 빼 보세요."
              : "검색어나 필터를 바꿔 보세요."
          }
        />
      ) : null}

      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(problem) => problem.id}
            href={(problem) => `/problems/${problem.id}`}
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
