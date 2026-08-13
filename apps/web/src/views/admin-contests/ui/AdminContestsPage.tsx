"use client";

import { adminContestApi } from "@/entities/contest";
import type { AdminContest } from "@/entities/contest";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Badge, Button, EmptyState, Pagination, Table, useToast } from "@/shared/ui";
import { formatDateTime } from "@/shared/lib";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 대회 관리 (#335).
 *
 * **API 는 다 있는데 화면이 없었다** — `CONTEST_MANAGER` 역할이 있는데 그 사람이
 * 어드민에서 할 수 있는 일이 하나도 없었다.
 */
export function AdminContestsPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<AdminContest> | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    adminContestApi
      .list({ page, size: 20 })
      .then(setResult)
      .catch(() => setResult(null));
  }, [page, reloadKey]);

  const changeStatus = async (contest: AdminContest, status: string) => {
    try {
      await adminContestApi.changeStatus(contest.id, status);
      toast.success("상태를 바꿨습니다.");
      setReloadKey((key) => key + 1);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "바꾸지 못했습니다.");
    }
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center gap-3">
        <div>
          <h1 className="text-2xl font-bold text-ink">대회 관리</h1>
          <p className="mt-1 text-sm text-ink-muted">
            진행 중인 대회는 고칠 수 없습니다 — 공지와 질의 답변은 대회 화면에서 그대로 됩니다.
          </p>
        </div>
        <Link href="/admin/contests/new" className="ml-auto">
          <Button>새 대회</Button>
        </Link>
      </header>

      {result && result.content.length === 0 ? (
        <EmptyState title="아직 대회가 없습니다." description="새 대회를 만들어 보세요." />
      ) : null}

      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(row) => row.id}
            columns={[
              { key: "title", header: "제목", render: (row) => row.title },
              {
                key: "phase",
                header: "상태",
                render: (row) => (
                  <Badge tone={row.phase === "RUNNING" ? "ok" : "muted"}>{row.phaseLabel}</Badge>
                ),
              },
              {
                key: "when",
                header: "기간",
                hideBelow: "sm",
                render: (row) => (
                  <span className="text-xs text-ink-muted">
                    {formatDateTime(row.startsAt)} ~ {formatDateTime(row.endsAt)}
                  </span>
                ),
              },
              {
                key: "actions",
                header: "",
                align: "right",
                render: (row) => (
                  <span className="flex flex-wrap justify-end gap-1">
                    {/*
                      **왜 못 고치는지 보인다** (#335). 버튼만 비활성이면 고장으로 읽힌다.
                    */}
                    {row.phase === "RUNNING" ? (
                      <span className="text-xs text-ink-muted">진행 중이라 수정할 수 없습니다</span>
                    ) : (
                      <Link href={`/admin/contests/${row.id}`}>
                        <Button variant="ghost" className="px-2 py-0.5 text-xs">
                          수정
                        </Button>
                      </Link>
                    )}
                    {row.status === "DRAFT" ? (
                      <Button
                        variant="ghost"
                        className="px-2 py-0.5 text-xs"
                        onClick={() => changeStatus(row, "PUBLISHED")}
                      >
                        공개
                      </Button>
                    ) : null}
                    {/* 대회 운영(공지·질의·순위표)은 대회 화면에서 한다 — 두 벌을 만들지 않는다. */}
                    <Link href={`/contests/${row.slug}`}>
                      <Button variant="ghost" className="px-2 py-0.5 text-xs">
                        운영 화면
                      </Button>
                    </Link>
                  </span>
                ),
              },
            ]}
          />
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
