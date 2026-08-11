"use client";

import { TierBadge, problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { Field, Input } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 문제를 찾아 담는다 (#87).
 *
 * **문제 번호를 외워서 입력하는 방식은 안 된다.** 제목 일부로 찾고, 티어와 정답률을
 * 함께 보여줘 고르기 쉽게 한다 — 무엇을 담을지 고르는 것이 문제집 만들기의 전부다.
 */
export function ProblemPicker({
  pickedIds,
  onPick,
}: {
  pickedIds: number[];
  onPick: (problem: ProblemSummary) => void;
}) {
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<ProblemSummary[]>([]);

  useEffect(() => {
    if (keyword.trim().length === 0) return;

    // 글자마다 요청하지 않도록 잠깐 기다렸다가 찾는다.
    const timer = setTimeout(() => {
      problemApi
        .list({ q: keyword, size: 8, sort: "DIFFICULTY" })
        .then((page) => setResults(page.content))
        .catch(() => setResults([]));
    }, 200);
    return () => clearTimeout(timer);
  }, [keyword]);

  // 검색어를 지우면 결과도 사라져야 한다. 상태를 비우는 대신 그리지 않는다 —
  // 이펙트에서 상태를 만지면 렌더가 한 번 더 돈다.
  const visible = keyword.trim().length === 0 ? [] : results;

  return (
    <div className="space-y-2">
      <Field label="문제 찾기">
        <Input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="제목 일부를 입력하세요"
        />
      </Field>

      {visible.length > 0 ? (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {visible.map((problem) => {
            const picked = pickedIds.includes(problem.id);
            return (
              <li key={problem.id}>
                <button
                  type="button"
                  disabled={picked}
                  onClick={() => onPick(problem)}
                  className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm transition enabled:hover:bg-surface-muted disabled:opacity-50"
                >
                  <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
                  <span className="min-w-0 flex-1 truncate text-ink">{problem.title}</span>
                  {/* 정답률을 함께 보여줘야 난이도만으로는 안 보이는 체감 난이도가 드러난다. */}
                  <span className="shrink-0 text-xs text-ink-muted">
                    {problem.stats.solverCount}명
                    {problem.stats.acceptanceRate !== null ? ` · ${problem.stats.acceptanceRate}%` : ""}
                  </span>
                  {/* 이미 담긴 문제를 표시하지 않으면 같은 것을 또 누르게 된다. */}
                  <span className="shrink-0 text-xs text-ink-muted">{picked ? "담김" : "담기"}</span>
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}
