"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Alert, Button, EmptyState, Pagination, Table, useToast } from "@/shared/ui";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { adminProblemColumns } from "./adminProblemColumns";
import { AdminProblemBulkBar } from "./AdminProblemBulkBar";
import { AdminProblemFilters, EMPTY_FILTERS } from "./AdminProblemFilters";
import type { AdminProblemFilterValues } from "./AdminProblemFilters";
import { isFiltered, toQuery } from "./adminProblemQuery";

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
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const toast = useToast();

  const load = useCallback(() => {
    // 전에는 첫 50개만 불러서 **51번째 문제에 화면으로 도달할 수 없었다** (#131).
    problemApi
      .adminList({ ...toQuery(filters), page, size: 20 })
      .then(setResult)
      .catch(() => setError("문제 목록을 불러오지 못했습니다."));
  }, [filters, page]);

  useEffect(load, [load]);

  /*
    **장을 넘기거나 조건을 바꾸면 선택이 풀린다** (#627).

    고른 것을 들고 다니면 **보이지 않는 것에 일괄로 걸게 된다** — 3장에서 고른 것을
    잊은 채 1장에서 "공개하기" 를 누르면, 화면에 없는 스무 개가 함께 공개된다.
    되돌릴 수는 있지만 그 사실을 알아채는 것이 먼저다.
  */
  const changePage = (next: number) => {
    setPage(next);
    setSelected(new Set());
  };

  /** 조건이 바뀌면 **첫 장으로 돌아간다.** 3페이지에서 조건을 좁히면 빈 화면이 나온다. */
  const changeFilters = (next: Partial<AdminProblemFilterValues>) => {
    setFilters((current) => ({ ...current, ...next }));
    changePage(0);
  };

  const pageIds = result?.content.map((problem) => problem.id) ?? [];

  const toggle = (id: number) =>
    setSelected((current) => {
      const next = new Set(current);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  const toggleAll = () =>
    setSelected((current) => (pageIds.every((id) => current.has(id)) ? new Set() : new Set(pageIds)));

  const publish = async (published: boolean) => {
    try {
      const result = await problemApi.publish([...selected], published);
      const word = published ? "공개로" : "비공개로";
      // **바꾼 수를 그대로 말한다.** 이미 그 상태였던 것은 세지 않는다 — "20개를
      // 공개했습니다" 인데 실제로는 3개만 바뀌었다면 다음에 그 말을 믿지 않게 된다.
      toast.success(
        `${result.changed}개를 ${word} 바꿨습니다.` +
          (result.missing.length > 0 ? ` (${result.missing.length}개는 찾지 못했습니다)` : ""),
      );
      setSelected(new Set());
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "바꾸지 못했습니다.");
    }
  };

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

      <AdminProblemFilters values={filters} onChange={changeFilters} />

      <AdminProblemBulkBar count={selected.size} onPublish={publish} onClear={() => setSelected(new Set())} />

      {error ? <Alert>{error}</Alert> : null}

      {result && result.content.length === 0 ? (
        /*
          **아무것도 없는 것과 못 찾은 것을 구별한다.** 전에는 조건을 좁혀 0건이 되어도
          "첫 문제를 등록해 보세요" 가 떴다 — 195개가 있는데도 그렇게 보인다.
        */
        isFiltered(filters) ? (
          <EmptyState title="조건에 맞는 문제가 없습니다." description="검색어나 조건을 바꿔 보세요." />
        ) : (
          <EmptyState title="등록된 문제가 없습니다." description="첫 문제를 등록해 보세요." />
        )
      ) : null}

      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(problem) => problem.id}
            columns={adminProblemColumns(remove, {
              ids: selected,
              onToggle: toggle,
              onToggleAll: toggleAll,
              pageIds,
            })}
          />
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onChange={changePage}
          />
        </>
      ) : null}
    </div>
  );
}
