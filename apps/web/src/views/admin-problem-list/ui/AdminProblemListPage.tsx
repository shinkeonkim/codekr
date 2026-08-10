"use client";

import { CATEGORY_LABELS, TierBadge, problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { RequireAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Alert, Badge, Button, Card, EmptyState } from "@/shared/ui";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

export function AdminProblemListPage() {
  return (
    <RequireAuth adminOnly>
      <AdminProblemList />
    </RequireAuth>
  );
}

function AdminProblemList() {
  const [result, setResult] = useState<Page<ProblemSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    problemApi
      .adminList({ page: 0, size: 50 })
      .then(setResult)
      .catch(() => setError("문제 목록을 불러오지 못했습니다."));
  }, []);

  useEffect(load, [load]);

  const remove = async (id: number, title: string) => {
    if (!confirm(`'${title}' 문제를 삭제할까요? (제출 이력은 그대로 남습니다)`)) return;
    try {
      await problemApi.remove(id);
      setError(null);
      load();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "삭제하지 못했습니다.");
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-bold text-ink">문제 관리</h1>
        <Link href="/admin/queues" className="text-sm text-ink-muted hover:text-ink">
          큐 모니터링
        </Link>
        <Link href="/admin/problems/new" className="ml-auto">
          <Button>문제 등록</Button>
        </Link>
      </div>

      {error ? <Alert>{error}</Alert> : null}

      {result && result.content.length === 0 ? (
        <EmptyState title="등록된 문제가 없습니다." description="첫 문제를 등록해 보세요." />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((problem) => (
          <Card key={problem.id} className="flex items-center gap-3 px-5 py-3">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-ink">{problem.title}</p>
              <p className="mt-0.5 text-xs text-ink-muted">
                {problem.slug} · {CATEGORY_LABELS[problem.category]}
              </p>
            </div>
            <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
            <Badge tone={problem.published ? "ok" : "muted"}>
              {problem.published ? "공개" : "미공개"}
            </Badge>
            <Link href={`/admin/problems/${problem.id}/edit`}>
              <Button variant="secondary">수정</Button>
            </Link>
            <Button variant="danger" onClick={() => remove(problem.id, problem.title)}>
              삭제
            </Button>
          </Card>
        ))}
      </div>
    </div>
  );
}
