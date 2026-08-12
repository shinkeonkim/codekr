"use client";

import { userApi } from "@/entities/user";
import type { AdminAuditLog } from "@/entities/user";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Badge, EmptyState, Field, Input, Pagination, Table } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 어드민 관리 기록 (#225).
 *
 * **로그로는 안 됐다** — 지나가고, 찾을 수 없고, 화면에 없었다. 역할 변경(#103)과
 * 강제 탈퇴(#140)가 일어난 뒤에 "누가 왜" 를 물을 곳이 없었다.
 */
export function AdminAuditLogPage() {
  const [targetUserId, setTargetUserId] = useState("");
  const [actorId, setActorId] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<AdminAuditLog> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    userApi
      .auditLogs({
        targetUserId: numberOrUndefined(targetUserId),
        actorId: numberOrUndefined(actorId),
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
        setError(caught instanceof ApiError ? caught.message : "기록을 불러오지 못했습니다.");
      });
    return () => {
      cancelled = true;
    };
  }, [targetUserId, actorId, page]);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-ink">관리 기록</h1>
        <p className="mt-1 text-sm text-ink-muted">
          역할 변경과 강제 탈퇴가 남습니다. 고치거나 지울 수 없고, 덧붙이기만 됩니다.
        </p>
      </div>

      {/* 두 방향으로 묻는다 — "이 회원에게 무슨 일이 있었나" 와 "이 어드민이 무엇을 했나". */}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="대상 회원 ID">
          <Input
            inputMode="numeric"
            placeholder="예: 42"
            value={targetUserId}
            onChange={(event) => {
              setTargetUserId(event.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field label="집행한 어드민 ID">
          <Input
            inputMode="numeric"
            placeholder="예: 1"
            value={actorId}
            onChange={(event) => {
              setActorId(event.target.value);
              setPage(0);
            }}
          />
        </Field>
      </div>

      {error ? <EmptyState title={error} /> : null}

      {result && result.content.length === 0 ? (
        <EmptyState title="기록이 없습니다." description="조건을 바꾸거나 비워서 전체를 보세요." />
      ) : null}

      {result && result.content.length > 0 ? (
        <>
          <Table
            rows={result.content}
            rowKey={(row) => row.id}
            columns={[
              {
                key: "createdAt",
                header: "시각",
                render: (row) => (
                  <span className="whitespace-nowrap text-xs text-ink-muted">{formatDateTime(row.createdAt)}</span>
                ),
              },
              {
                key: "action",
                header: "한 일",
                render: (row) => (
                  <Badge tone={row.action === "FORCE_WITHDRAW" ? "danger" : "info"}>{row.actionLabel}</Badge>
                ),
              },
              {
                key: "actor",
                header: "집행",
                render: (row) => (
                  <span>
                    {row.actorNickname ?? "-"} <span className="text-ink-muted tabular-nums">#{row.actorId}</span>
                  </span>
                ),
              },
              {
                key: "target",
                header: "대상",
                // **탈퇴는 닉네임을 지운다** (#140). 그때의 이름을 사본으로 들고 있어야
                // 숫자만 남지 않는다.
                render: (row) => (
                  <span>
                    {row.targetLabel ?? "-"} <span className="text-ink-muted tabular-nums">#{row.targetId}</span>
                  </span>
                ),
              },
              {
                key: "detail",
                header: "내용",
                hideBelow: "lg",
                render: (row) => <span className="text-ink-muted">{row.detail ?? "-"}</span>,
              },
              {
                key: "reason",
                header: "사유",
                hideBelow: "sm",
                render: (row) => <span className="text-ink-muted">{row.reason ?? "-"}</span>,
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

/** 빈 칸과 숫자가 아닌 입력은 "거르지 않음" 으로 본다 — 걸러진 채로 멈추지 않게. */
function numberOrUndefined(value: string): number | undefined {
  const parsed = Number(value.trim());
  return value.trim() && Number.isFinite(parsed) ? parsed : undefined;
}
