"use client";

import { userApi } from "@/entities/user";
import type { AdminUserSummary } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { formatDate } from "@/shared/lib";
import { Badge, Button, EmptyState, Field, Input, Pagination, Select, Table, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";
import { AdminUserActions } from "./AdminUserActions";

/**
 * 어드민 회원 관리 (#223).
 *
 * **역할 변경(#103)과 강제 탈퇴(#140)는 API 만 있고 화면이 없었다.** 만들어 두고 쓸 수
 * 없는 기능으로 남아 있었고, 회원을 찾으려면 DB 를 직접 봐야 했다.
 */
export function AdminUserListPage() {
  const toast = useToast();
  const { user } = useAuth();
  const [keyword, setKeyword] = useState("");
  const [role, setRole] = useState("");
  const [includeWithdrawn, setIncludeWithdrawn] = useState(false);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<AdminUserSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 작업(역할 변경·강제 탈퇴) 뒤에 다시 읽는 방아쇠. 상태를 하나 올려 효과를 다시 돌린다.
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    userApi
      .adminSearch({
        // 서버가 두 글자 미만을 막는다 (#223). 한 글자로 이메일 목록을 훑지 못하게.
        q: keyword.trim().length >= 2 ? keyword.trim() : undefined,
        role: role || undefined,
        includeWithdrawn: includeWithdrawn ? "true" : undefined,
        page,
        size: 20,
      })
      .then((data) => {
        if (cancelled) return;
        setResult(data);
        setError(null);
      })
      .catch((caught) => {
        if (cancelled) return;
        setError(caught instanceof ApiError ? caught.message : "회원을 불러오지 못했습니다.");
      });
    return () => {
      cancelled = true;
    };
  }, [keyword, role, includeWithdrawn, page, reloadKey]);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-ink">회원 관리</h1>
        <p className="mt-1 text-sm text-ink-muted">
          닉네임이나 이메일로 찾습니다. 재계산·역할 변경에 필요한 회원 ID 가 목록에 있습니다.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="검색">
          <Input
            placeholder="닉네임 또는 이메일 (2글자 이상)"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field label="역할">
          <Select
            value={role}
            onChange={(event) => {
              setRole(event.target.value);
              setPage(0);
            }}
          >
            <option value="">전체</option>
            <option value="SUPERUSER">최고 관리자</option>
            <option value="ADMIN">관리자</option>
            <option value="PROBLEM_SETTER">문제 출제자</option>
            <option value="CONTEST_MANAGER">대회 운영자</option>
            <option value="BOARD_MANAGER">게시판 관리자</option>
          </Select>
        </Field>
        <Field label="탈퇴 회원">
          <Select
            value={includeWithdrawn ? "true" : ""}
            onChange={(event) => {
              setIncludeWithdrawn(event.target.value === "true");
              setPage(0);
            }}
          >
            <option value="">빼고 보기</option>
            <option value="true">함께 보기</option>
          </Select>
        </Field>
      </div>

      {error ? <EmptyState title={error} /> : null}

      {result && result.content.length === 0 ? (
        <EmptyState title="조건에 맞는 회원이 없습니다." description="검색어를 바꿔 보세요." />
      ) : null}

      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(row) => row.id}
            columns={[
              // 재계산 같은 작업이 id 를 요구한다 — 목록에서 바로 집어낼 수 있어야 한다.
              { key: "id", header: "ID", width: "w-20", render: (row) => <span className="tabular-nums">{row.id}</span> },
              { key: "nickname", header: "닉네임", render: (row) => row.nickname },
              {
                key: "email",
                header: "이메일",
                hideBelow: "sm",
                // 이메일은 길어서 줄을 밀어낸다. 잘라 두고 전체는 상세에서 본다.
                render: (row) => <span className="block max-w-56 truncate text-ink-muted">{row.email}</span>,
              },
              {
                key: "roles",
                header: "역할",
                hideBelow: "sm",
                render: (row) => (
                  <span className="flex flex-wrap gap-1">
                    {row.roles.map((each) => (
                      <Badge key={each} tone={each === "USER" ? "muted" : "info"}>
                        {each}
                      </Badge>
                    ))}
                  </span>
                ),
              },
              {
                key: "createdAt",
                header: "가입일",
                hideBelow: "lg",
                align: "right",
                render: (row) => <span className="text-xs text-ink-muted">{formatDate(row.createdAt)}</span>,
              },
              {
                key: "state",
                header: "상태",
                align: "right",
                // 정지는 목록에서 보여야 한다 (#224) — 상세를 하나씩 열어 봐야 안다면
                // 이미 정지된 사람을 또 정지시킨다.
                render: (row) =>
                  row.withdrawnAt ? (
                    <Badge tone="danger">탈퇴</Badge>
                  ) : row.suspendedScopes.length > 0 ? (
                    <Badge tone="danger">{row.suspendedScopes.join("·")} 제한</Badge>
                  ) : row.emailVerifiedAt === null ? (
                    // 인증을 안 하면 글도 댓글도 못 쓴다 (#233, #524). 정지와 다른
                    // 이유로 막힌 상태라 색도 다르다 — 이쪽은 우리가 도울 수 있다.
                    <Badge tone="warn">이메일 미인증</Badge>
                  ) : (
                    <Badge tone="ok">활동</Badge>
                  ),
              },
              {
                key: "actions",
                header: "작업",
                align: "right",
                render: (row) => (
                  <AdminUserActions
                    target={row}
                    // **자기 자신에게는 감춘다.** 서버도 막지만(자기 SUPERUSER 회수 금지),
                    // 누르면 실패하는 버튼은 고장으로 보인다.
                    self={user?.nickname === row.nickname}
                    onDone={(message) => {
                      toast.success(message);
                      setReloadKey((key) => key + 1);
                    }}
                    onError={(message) => toast.error(message)}
                  />
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
