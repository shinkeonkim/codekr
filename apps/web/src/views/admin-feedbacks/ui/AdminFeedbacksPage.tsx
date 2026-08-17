"use client";

import { feedbackApi } from "@/entities/feedback";
import type { FeedbackStatus, SiteFeedback } from "@/entities/feedback";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Alert, EmptyState, Pagination, useToast } from "@/shared/ui";
import { useCallback, useEffect, useState } from "react";
import { FeedbackCard } from "./FeedbackCard";

const FILTERS: { value: FeedbackStatus | ""; label: string }[] = [
  { value: "OPEN", label: "아직 안 본 것" },
  { value: "ACCEPTED", label: "반영했음" },
  { value: "REJECTED", label: "반영하지 않음" },
  { value: "", label: "전부" },
];

/**
 * 들어온 사이트 신고·제안을 처리한다 (#603).
 *
 * **폼과 이 화면은 함께 나가야 한다** — 폼만 있으면 아무도 안 보는 곳에 쌓이고
 * 넣은 사람은 알렸다고 믿는다. #548 이 같은 이유로 같이 나갔다.
 *
 * 문제 오류 신고(`/admin/problem-reports`)와 자리를 가른다. 저기는 문제를 고쳐야
 * 끝나고 출제자가 보지만, 여기는 사이트 전체 이야기라 어드민이 본다.
 */
export function AdminFeedbacksPage() {
  const toast = useToast();
  const [status, setStatus] = useState<FeedbackStatus | "">("OPEN");
  const [result, setResult] = useState<Page<SiteFeedback> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    feedbackApi
      .list({ status: status || undefined, page, size: 20 })
      .then(setResult)
      .catch(() => setError("불러오지 못했습니다."));
  }, [status, page]);

  useEffect(load, [load]);

  const resolve = async (id: number, next: FeedbackStatus, resolution: string) => {
    try {
      await feedbackApi.resolve(id, { status: next, resolution: resolution || undefined });
      toast.success("처리했습니다.");
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "처리하지 못했습니다.");
    }
  };

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-2xl font-bold text-ink">신고·제안</h1>
        <p className="mt-1 text-sm text-ink-muted">
          문제에 매이지 않은 것들입니다. 지문·테스트케이스 이야기는 오류 신고로 갑니다.
        </p>
      </header>

      <nav className="flex flex-wrap gap-1 text-sm">
        {FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => {
              setPage(0);
              setStatus(filter.value);
            }}
            aria-current={status === filter.value}
            className="rounded-full px-3 py-1 text-ink-muted transition hover:text-ink aria-[current=true]:bg-surface-muted aria-[current=true]:font-medium aria-[current=true]:text-ink"
          >
            {filter.label}
          </button>
        ))}
      </nav>

      {error ? <Alert>{error}</Alert> : null}

      {result && result.content.length === 0 ? (
        <EmptyState title={status === "OPEN" ? "처리할 것이 없습니다." : "해당하는 것이 없습니다."} />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((feedback) => (
          <FeedbackCard key={feedback.id} feedback={feedback} onResolve={resolve} />
        ))}
      </div>

      {result ? (
        <Pagination
          page={result.page}
          totalPages={result.totalPages}
          totalElements={result.totalElements}
          onChange={setPage}
        />
      ) : null}
    </div>
  );
}
