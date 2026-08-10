"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Badge, Button, Card, EmptyState, Input, Select } from "./ui";
import { api } from "@/lib/api";
import { STATUS_LABELS, VERDICT_LABELS, formatDateTime, verdictTone } from "@/lib/labels";
import type { Page, Runtime, SubmissionSummary, Verdict } from "@/lib/types";

const VERDICTS: Verdict[] = [
  "ACCEPTED",
  "WRONG_ANSWER",
  "TIME_LIMIT_EXCEEDED",
  "MEMORY_LIMIT_EXCEEDED",
  "RUNTIME_ERROR",
  "COMPILE_ERROR",
  "OUTPUT_LIMIT_EXCEEDED",
  "SYSTEM_ERROR",
];

const SORTS = [
  { value: "LATEST", label: "최신순" },
  { value: "OLDEST", label: "오래된순" },
  { value: "RUNTIME", label: "실행 시간 짧은순" },
  { value: "MEMORY", label: "메모리 적은순" },
];

/** URL 쿼리에 담는 필터 키. 새로고침·링크 공유 후에도 같은 목록이 나오게 한다. */
const FILTER_KEYS = ["problemSlug", "nickname", "runtimeId", "verdict", "from", "to", "sort", "page"] as const;
type FilterKey = (typeof FILTER_KEYS)[number];
type Filters = Partial<Record<FilterKey, string>>;

interface Props {
  /** 문제 상세 안에서 쓸 때는 그 문제로 범위를 고정한다. */
  fixedProblemSlug?: string;
  emptyMessage?: string;
}

export function SubmissionExplorer({ fixedProblemSlug, emptyMessage }: Props) {
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
    api.runtimes().then(setRuntimes).catch(() => setRuntimes([]));
  }, []);

  useEffect(() => {
    api
      .exploreSubmissions({ ...filters, problemSlug: fixedProblemSlug ?? filters.problemSlug, size: 20 })
      .then((response) => {
        setResult(response);
        setError(null);
      })
      .catch(() => setError("제출 내역을 불러오지 못했습니다."));
    // searchParams 문자열이 바뀔 때만 다시 조회한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams.toString(), fixedProblemSlug]);

  const hasFilters = FILTER_KEYS.some((key) => key !== "page" && filters[key]);

  return (
    <div className="space-y-4">
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        {fixedProblemSlug ? null : (
          <Input
            placeholder="문제 slug"
            value={filters.problemSlug ?? ""}
            onChange={(event) => setFilter("problemSlug", event.target.value)}
          />
        )}
        <Input
          placeholder="닉네임"
          value={filters.nickname ?? ""}
          onChange={(event) => setFilter("nickname", event.target.value)}
        />
        <Select value={filters.runtimeId ?? ""} onChange={(event) => setFilter("runtimeId", event.target.value)}>
          <option value="">전체 언어</option>
          {runtimes.map((runtime) => (
            <option key={runtime.id} value={runtime.id}>
              {runtime.label}
            </option>
          ))}
        </Select>
        <Select value={filters.verdict ?? ""} onChange={(event) => setFilter("verdict", event.target.value)}>
          <option value="">전체 판정</option>
          {VERDICTS.map((verdict) => (
            <option key={verdict} value={verdict}>
              {VERDICT_LABELS[verdict]}
            </option>
          ))}
        </Select>
        <Input
          type="date"
          aria-label="시작일"
          value={filters.from ?? ""}
          onChange={(event) => setFilter("from", event.target.value)}
        />
        <Input
          type="date"
          aria-label="종료일"
          value={filters.to ?? ""}
          onChange={(event) => setFilter("to", event.target.value)}
        />
        <Select value={filters.sort ?? "LATEST"} onChange={(event) => setFilter("sort", event.target.value)}>
          {SORTS.map((sort) => (
            <option key={sort.value} value={sort.value}>
              {sort.label}
            </option>
          ))}
        </Select>
        {hasFilters ? (
          <Button type="button" variant="secondary" onClick={() => router.replace(pathname, { scroll: false })}>
            필터 초기화
          </Button>
        ) : null}
      </div>

      {error ? <EmptyState title={error} /> : null}

      {result && result.content.length === 0 ? (
        <EmptyState
          title={emptyMessage ?? "조건에 맞는 제출이 없습니다."}
          description={hasFilters ? "필터를 바꿔 보세요." : undefined}
        />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((submission) => (
          <Card key={submission.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
            <Link href={`/submissions/${submission.id}`} className="min-w-0 flex-1">
              <p className="truncate font-medium text-ink">{submission.problemTitle}</p>
              <p className="mt-0.5 text-xs text-ink-muted">
                {submission.nickname} · {submission.runtimeId} · {formatDateTime(submission.createdAt)}
              </p>
            </Link>
            {submission.sourceVisible ? null : (
              <span className="text-xs text-ink-muted" title="작성자가 코드를 공개하지 않았습니다">
                코드 비공개
              </span>
            )}
            {submission.verdict ? (
              <Badge tone={verdictTone(submission.verdict)}>
                {VERDICT_LABELS[submission.verdict]} · {submission.passedCount}/{submission.totalCount}
              </Badge>
            ) : (
              <Badge>{STATUS_LABELS[submission.status]}</Badge>
            )}
          </Card>
        ))}
      </div>

      {result && result.totalPages > 1 ? (
        <div className="flex items-center justify-center gap-2 text-sm">
          <Button
            variant="secondary"
            disabled={result.page === 0}
            onClick={() => setFilter("page", String(result.page - 1))}
          >
            이전
          </Button>
          <span className="text-ink-muted">
            {result.page + 1} / {result.totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={result.page + 1 >= result.totalPages}
            onClick={() => setFilter("page", String(result.page + 1))}
          >
            다음
          </Button>
        </div>
      ) : null}
    </div>
  );
}
