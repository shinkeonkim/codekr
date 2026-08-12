"use client";

import { TierBadge, problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { Card } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/** 옆의 공지와 높이를 맞춘 수다 (#275). */
const LIMIT = 5;

/**
 * 첫 화면의 본론. **바로 누를 수 있는 문제**를 보여준다.
 *
 * 기능 소개 카드 세 장보다 문제 목록 하나가 낫다 — 방문자가 여기서 하려는 일이
 * 문제를 푸는 것이기 때문이다.
 *
 * 폭을 스스로 정하지 않는다 (#275). 공지와 나란히 설지, 혼자 폭을 다 쓸지는
 * 공지가 있는지에 달렸고 그것은 `HomePage` 만 안다.
 */
export function StartHere() {
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null);

  useEffect(() => {
    // 쉬운 것부터. 처음 온 사람이 바로 풀 수 있어야 한다.
    problemApi
      .list({ sort: "DIFFICULTY", size: LIMIT })
      .then((page) => setProblems(page.content))
      .catch(() => setProblems([]));
  }, []);

  return (
    <section className="space-y-4">
      <div className="flex items-baseline justify-between">
        <h2 className="text-lg font-semibold text-ink">쉬운 문제부터</h2>
        <Link href="/problems" className="text-sm text-brand hover:underline">
          전체 문제 보기 →
        </Link>
      </div>

      {problems === null ? (
        <p className="py-8 text-center text-sm text-ink-muted">불러오는 중…</p>
      ) : problems.length === 0 ? (
        <Card className="px-6 py-10 text-center text-sm text-ink-muted">
          아직 공개된 문제가 없습니다.
        </Card>
      ) : (
        <Card className="divide-y divide-border">
          {problems.map((problem) => (
            <Link
              key={problem.slug}
              href={`/problems/${problem.id}`}
              className="flex items-center gap-3 px-5 py-3.5 transition hover:bg-surface-muted/40"
            >
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">
                {problem.title}
              </span>
              <TierBadge difficulty={problem.difficulty} />
            </Link>
          ))}
        </Card>
      )}
    </section>
  );
}
