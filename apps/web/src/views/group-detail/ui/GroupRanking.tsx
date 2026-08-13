"use client";

import { groupApi } from "@/entities/group";
import type { RankingEntry } from "@/entities/ranking";
import { UserLink } from "@/entities/user";
import { Card, CardTitle, Pagination } from "@/shared/ui";
import { useEffect, useState } from "react";

const PAGE_SIZE = 20;

/**
 * 그룹 안 랭킹 (#402, #240 7단계).
 *
 * **모집단을 좁힐 뿐 정렬을 바꾸지 않는다** — 소속 안 랭킹(#399)과 같다. 등수는 그
 * 안에서 1위부터 다시 매겨진다: "우리 스터디에서 2등" 이 이 기능의 이유다.
 *
 * 랭킹 화면이 아니라 **그룹 화면에 있다.** 멤버만 볼 수 있는 것이라, 아무나 여는
 * 순위표에 그룹을 고르는 칸을 두면 그 규칙이 어긋난다.
 */
export function GroupRanking({ groupId }: { groupId: number }) {
  const [entries, setEntries] = useState<RankingEntry[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);

  useEffect(() => {
    groupApi
      .ranking(groupId, { metric: "SCORE", period: "ALL_TIME", page, size: PAGE_SIZE })
      .then((result) => {
        setEntries(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      // 그룹을 못 볼 사람은 이 화면에 오지 못한다. 그래도 조용히 비워 둔다.
      .catch(() => setEntries([]));
  }, [groupId, page]);

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>순위</CardTitle>
        {/* 무엇을 재는 숫자인지 모르면 순위는 그냥 줄 세우기다. */}
        <p className="mt-1 text-xs text-ink-muted">
          이 그룹 안에서만 매긴 실력 점수 순위입니다. 아직 못 푼 사람도 함께 보입니다.
        </p>
      </div>

      {entries.map((entry) => (
        <div key={entry.nickname} className="flex items-center gap-3 text-sm">
          <span className="w-6 shrink-0 tabular-nums text-ink-muted">{entry.rank}</span>
          <span className="flex-1 truncate">
            <UserLink nickname={entry.nickname} />
          </span>
          <span className="w-20 text-right tabular-nums text-ink">
            {entry.score.toLocaleString()}점
          </span>
          <span className="hidden w-20 text-right tabular-nums text-ink-muted sm:inline">
            {entry.solvedCount}문제
          </span>
        </div>
      ))}

      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onChange={setPage}
      />
    </Card>
  );
}
