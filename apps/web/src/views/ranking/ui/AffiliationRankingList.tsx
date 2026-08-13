"use client";

import { rankingApi } from "@/entities/ranking";
import type { AffiliationRankingEntry } from "@/entities/ranking";
import { ApiError } from "@/shared/api";
import { Alert, Badge, Card, EmptyState, Pagination } from "@/shared/ui";
import { useEffect, useState } from "react";

const PAGE_SIZE = 50;

/**
 * 소속끼리 겨루는 랭킹 (#400, #240 5단계).
 *
 * **점수는 상위 다섯 명의 합이다.** 합이면 사람 많은 곳이 언제나 이기고, 평균이면
 * 잘하는 사람만 남기고 **내보내는 유인**이 생긴다. 상위 N명 합은 둘 다 피한다 —
 * 여섯 번째 사람이 들어와도 순위가 안 바뀌므로 끌어들일 이유도 내보낼 이유도 없다.
 */
export function AffiliationRankingList({
  period,
  page,
  onPageChange,
}: {
  period: string;
  page: number;
  onPageChange: (next: number) => void;
}) {
  const [entries, setEntries] = useState<AffiliationRankingEntry[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    rankingApi
      .affiliations({ period, page, size: PAGE_SIZE })
      .then((result) => {
        setEntries(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
        setError(null);
      })
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "랭킹을 불러오지 못했습니다."),
      );
  }, [period, page]);

  return (
    <>
      {/* 숫자가 무엇의 합인지 모르면 순위는 그냥 줄 세우기다. */}
      <p className="text-xs text-ink-muted">
        상위 5명의 실력 점수 합입니다. 5명 이상이 소속을 붙인 곳만 겨룹니다.
      </p>

      {error ? <Alert>{error}</Alert> : null}

      {entries.length === 0 && !error ? (
        <EmptyState mascot="award" title="아직 겨룰 수 있는 소속이 없습니다." />
      ) : (
        <Card className="divide-y divide-border p-0">
          {entries.map((entry) => (
            <div key={entry.affiliationId} className="flex items-center gap-4 px-5 py-3">
              <span className="w-10 shrink-0 text-sm font-semibold tabular-nums text-ink-muted">
                {entry.rank}
              </span>
              <span className="flex flex-1 items-center gap-2 truncate">
                <Badge tone="muted">{entry.kindLabel}</Badge>
                <span className="truncate text-sm text-ink">{entry.name}</span>
              </span>
              <span className="w-24 text-right text-sm font-semibold tabular-nums text-ink">
                {entry.score.toLocaleString()}점
              </span>
              {/* 인원은 순위를 가르지 않는다. 그래도 점수를 읽는 데는 맥락이다. */}
              <span className="hidden w-20 text-right text-xs tabular-nums text-ink-muted sm:inline">
                {entry.memberCount}명
              </span>
            </div>
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
