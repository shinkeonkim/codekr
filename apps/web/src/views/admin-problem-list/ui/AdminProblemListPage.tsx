"use client";

import { CATEGORY_LABELS, TierBadge, problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Alert, Badge, Button, Card, ConfirmDialog, EmptyState, Pagination } from "@/shared/ui";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";


export function AdminProblemListPage() {
  const [result, setResult] = useState<Page<ProblemSummary> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    // 전에는 첫 50개만 불러서 **51번째 문제에 화면으로 도달할 수 없었다** (#131).
    problemApi
      .adminList({ page, size: 20 })
      .then(setResult)
      .catch(() => setError("문제 목록을 불러오지 못했습니다."));
  }, [page]);

  useEffect(load, [load]);

  /** 되묻는 것은 표의 버튼(`ConfirmDialog`)이 한다 (#291 4단계). */
  const remove = async (id: number) => {
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
      {/* 구획 이동은 사이드바가 한다 (#179). 제목 옆에 또 두면 내비가 두 벌이 된다. */}
      {/* 좁은 화면에서 버튼 둘이 제목을 밀어내지 않게 접는다 (#484). */}
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-ink">문제 관리</h1>
        {/*
          만드는 방법이 둘이다. 폼(#13)은 테스트케이스가 몇 개일 때, 묶음(#479·#538)은
          수백 개일 때다. 묶음을 보조 버튼으로 둔 것은 지금 대부분이 폼이기 때문이고,
          테스트케이스가 많은 문제가 늘면 순서가 뒤집힐 수 있다.
        */}
        <Button asChild variant="secondary">
          <Link href="/admin/problems/import" className="ml-auto">묶음 올리기</Link>
        </Button>
        <Button asChild>
          <Link href="/admin/problems/new">문제 등록</Link>
        </Button>
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
            <Button asChild variant="secondary">
              <Link href={`/admin/problems/${problem.id}/edit`}>수정</Link>
            </Button>
            <ConfirmDialog
              title={`'${problem.title}' 문제를 삭제할까요?`}
              description="제출 이력은 그대로 남습니다. 문제만 목록에서 사라집니다."
              confirmLabel="삭제"
              onConfirm={() => remove(problem.id)}
              trigger={<Button variant="danger">삭제</Button>}
            />
          </Card>
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
