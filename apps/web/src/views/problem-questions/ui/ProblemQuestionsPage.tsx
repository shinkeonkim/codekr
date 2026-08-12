"use client";

import { postApi } from "@/entities/post";
import type { PostSummary } from "@/entities/post";
import { useProblem } from "@/entities/problem";
import { Avatar } from "@/entities/user";
import { useAuth } from "@/features/auth";
import type { Page } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Button, Card, EmptyState, Pagination } from "@/shared/ui";
import { ProblemHeader, ProblemTabs } from "@/widgets/problem-tabs";
import Link from "next/link";
import { use, useEffect, useState } from "react";

/**
 * 문제별 질문 탭 (#139).
 *
 * **문제를 보다가 페이지를 떠나지 않고 물을 수 있어야 한다.** 커뮤니티로 나가서
 * 어느 문제인지 다시 설명하고 글을 쓰라고 하면 대부분 쓰지 않는다.
 */
export function ProblemQuestionsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const { problem, error } = useProblem(decodeURIComponent(slug));
  const { user } = useAuth();
  const [result, setResult] = useState<Page<PostSummary> | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => {
    if (!problem) return;
    postApi.byProblem(problem.id, page).then(setResult).catch(() => setResult(null));
  }, [problem, page]);

  if (error) return <EmptyState title={error} />;
  if (!problem) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <ProblemHeader problem={problem} />
      <ProblemTabs slug={problem.slug} />

      <div className="flex items-center gap-3">
        <h2 className="text-sm font-semibold text-ink">질문 {result?.totalElements ?? 0}개</h2>
        {user ? (
          <Link href={`/posts/new?problemId=${problem.id}`} className="ml-auto">
            <Button>질문하기</Button>
          </Link>
        ) : null}
      </div>

      {result && result.content.length === 0 ? (
        // 첫 질문을 쓰게 만드는 자리다.
        <EmptyState
          mascot="thinking"
          title="아직 질문이 없습니다."
          description="막힌 곳을 남겨 두면 같은 곳에서 막힌 다음 사람이 먼저 읽고 갑니다."
        />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((question) => (
          <Link key={question.id} href={`/posts/${question.id}`} className="block">
            <Card className="flex flex-wrap items-center gap-3 p-4 transition hover:border-brand/50">
              <span className="min-w-0 flex-1 truncate font-medium text-ink">{question.title}</span>
              <span className="flex items-center gap-1.5 text-xs text-ink-muted">
                <Avatar
                  nickname={question.authorNickname}
                  avatarUrl={question.authorAvatarUrl}
                  size="sm"
                />
                {question.authorNickname}
              </span>
              {question.commentCount > 0 ? (
                <span className="text-xs text-ink-muted">답변 {question.commentCount}</span>
              ) : null}
              <span className="text-xs text-ink-muted">{formatDateTime(question.createdAt)}</span>
            </Card>
          </Link>
        ))}
        {result ? (
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onChange={setPage}
          />
        ) : null}
      </div>
    </div>
  );
}
