"use client";

import { rankingApi } from "@/entities/ranking";
import type { RankingOptions } from "@/entities/ranking";
import { useEffect, useState } from "react";
import { AffiliationFilter } from "./AffiliationFilter";
import { AffiliationRankingList } from "./AffiliationRankingList";
import { Choices } from "./Choices";
import { UserRankingList } from "./UserRankingList";

/**
 * 랭킹 (#57, #85, #58, #400).
 *
 * **고를 수 있는 축을 서버에서 받는다.** 지표나 기간이 늘어날 때 이 화면을 같이 고쳐야
 * 하는 구조를 만들지 않는다.
 *
 * 사람과 소속은 **같은 목록에 섞지 않는다** (#240 기획서 5절). 값의 뜻이 다르다 —
 * 사람은 자기 점수, 소속은 상위 다섯 명의 합이다.
 */
export function RankingPage() {
  const [options, setOptions] = useState<RankingOptions | null>(null);
  const [subject, setSubject] = useState("USER");
  const [metric, setMetric] = useState("SCORE");
  const [period, setPeriod] = useState("ALL_TIME");
  const [affiliationId, setAffiliationId] = useState<number | undefined>(undefined);
  const [page, setPage] = useState(0);

  /** 축을 바꾸면 1쪽으로 돌아간다 — 3쪽에서 지표를 바꾸면 순위표 한가운데가 열린다. */
  const axis = <T,>(set: (next: T) => void) => (next: T) => {
    set(next);
    setPage(0);
  };

  useEffect(() => {
    rankingApi.options().then(setOptions).catch(() => setOptions(null));
  }, []);

  const users = subject === "USER";
  const metricInfo = options?.metrics.find((it) => it.value === metric);

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">랭킹</h1>
        {/* 무엇을 재는 숫자인지 모르면 순위는 그냥 줄 세우기다. */}
        <p className="mt-1 text-xs text-ink-muted">
          {users ? (metricInfo?.description ?? " ") : "학교·회사끼리 겨룹니다."}
        </p>
      </header>

      <div className="flex flex-wrap gap-2">
        {/* 사람인가 소속인가. 서버가 주는 축이 아니라 **다른 순위표**라서 여기서 정한다. */}
        <Choices
          options={[
            { value: "USER", label: "사람" },
            { value: "AFFILIATION", label: "소속" },
          ]}
          value={subject}
          onChange={axis(setSubject)}
        />
        <Choices options={options?.periods ?? []} value={period} onChange={axis(setPeriod)} />
        {/* 지표와 소속 필터는 사람 순위표의 축이다. 소속끼리는 점수 합 하나로 겨룬다. */}
        {users ? (
          <>
            <Choices options={options?.metrics ?? []} value={metric} onChange={axis(setMetric)} />
            <AffiliationFilter value={affiliationId} onChange={axis(setAffiliationId)} />
          </>
        ) : null}
      </div>

      {users ? (
        <UserRankingList
          metric={metric}
          period={period}
          affiliationId={affiliationId}
          page={page}
          onPageChange={setPage}
        />
      ) : (
        <AffiliationRankingList period={period} page={page} onPageChange={setPage} />
      )}
    </div>
  );
}
