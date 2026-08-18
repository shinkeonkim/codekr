"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Alert, Button, EmptyState, Pagination, Table } from "@/shared/ui";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { adminProblemColumns } from "./adminProblemColumns";

/**
 * 어드민 문제 목록 (#625).
 *
 * **카드에서 표로 바꿨다.** 카드일 때는 티어와 공개 여부가 제목 길이를 따라 줄마다
 * 다른 자리에 떠서 **세로로 비교되지 않았고**, 창을 조금만 좁히면 삭제 버튼이 화면
 * 밖으로 밀려났다. 어드민 회원 목록도 사용자 문제 목록도 이미 표다 — 여기만 남아 있었다.
 */
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

      {result && result.content.length > 0 ? (
        <>
          <Table rows={result.content} rowKey={(problem) => problem.id} columns={adminProblemColumns(remove)} />
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onChange={setPage}
          />
        </>
      ) : null}
    </div>
  );
}
