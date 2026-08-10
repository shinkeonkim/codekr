"use client";

import { TierBadge, problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { useAuth } from "@/features/auth";
import { Button, Card } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 첫 화면의 본론. **바로 누를 수 있는 문제**를 보여준다.
 *
 * 기능 소개 카드 세 장보다 문제 목록 하나가 낫다 — 방문자가 여기서 하려는 일이
 * 문제를 푸는 것이기 때문이다.
 */
export function StartHere() {
  const { user, loading } = useAuth();
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null);

  useEffect(() => {
    // 쉬운 것부터. 처음 온 사람이 바로 풀 수 있어야 한다.
    problemApi
      .list({ sort: "DIFFICULTY", size: 5 })
      .then((page) => setProblems(page.content))
      .catch(() => setProblems([]));
  }, []);

  return (
    <section className="mx-auto max-w-3xl space-y-4">
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
        <ul className="space-y-2">
          {problems.map((problem) => (
            <li key={problem.slug}>
              <Link href={`/problems/${problem.slug}`}>
                <Card className="flex items-center gap-3 px-5 py-3.5 transition hover:border-brand/40">
                  <span className="truncate font-medium text-ink">{problem.title}</span>
                  <TierBadge difficulty={problem.difficulty} />
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {/* 로그인한 사람에게 회원가입을 권하지 않는다 (#73). */}
      {loading ? null : user ? null : (
        <div className="pt-4 text-center">
          <p className="mb-3 text-sm text-ink-muted">
            가입하면 푼 문제와 연속 학습 기록이 쌓입니다.
          </p>
          <Link href="/signup">
            <Button variant="secondary">회원가입</Button>
          </Link>
        </div>
      )}
    </section>
  );
}
