"use client";

import { groupApi } from "@/entities/group";
import type { AdminGroupRow } from "@/entities/group";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { Badge, Button, EmptyState, Field, Input, Pagination, Table, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 어드민 그룹 관리 (#438).
 *
 * **그룹은 누구나 만들고 이름도 아무거나 쓸 수 있다** (#401). 사칭을 구조적으로 막지
 * 않기로 했으므로 내리는 길이 있어야 한다 — 지금까지는 방장이 스스로 해산하는 길뿐이었고,
 * 문제가 되는 그룹의 방장이 그럴 이유는 없다.
 *
 * **명단은 여기서 보이지 않는다.** 그룹 안의 일은 그 안의 일이다 — 내릴지 판단하는 데
 * 필요한 것은 이름·방장·인원까지다.
 */
export function AdminGroupsPage() {
  const toast = useToast();
  const [keyword, setKeyword] = useState("");
  const [reason, setReason] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<AdminGroupRow> | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    groupApi
      .adminList({ q: keyword.trim() || undefined, page, size: 20 })
      .then(setResult)
      .catch(() => setResult(null));
  }, [keyword, page, reloadKey]);

  const takedown = async (row: AdminGroupRow) => {
    if (!reason.trim()) {
      // 사유는 **멤버 전원에게 그대로 간다.** 비운 채로 내리면 아무 말도 하지 않는 것이다.
      toast.error("사유를 먼저 적어 주세요.");
      return;
    }
    try {
      await groupApi.takedown(row.id, reason.trim());
      toast.success("내렸습니다. 멤버 전원에게 사유가 갔습니다.");
      setReloadKey((key) => key + 1);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "내리지 못했습니다.");
    }
  };

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-ink">그룹 관리</h1>
        <p className="mt-1 text-sm text-ink-muted">
          누구나 만들 수 있는 그룹입니다. 이름이 학교·회사와 같아도 그곳이 만든 것은
          아닙니다. 내린 사실은 관리 기록에 남습니다.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="이름">
          <Input
            placeholder="이름 일부"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field label="사유 (멤버 전원에게 그대로 전해집니다)">
          <Input
            placeholder="예: 학교 사칭"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
      </div>

      {result && result.content.length === 0 ? <EmptyState title="해당하는 그룹이 없습니다." /> : null}

      {result && result.content.length > 0 ? (
        <Table
          rows={result.content}
          rowKey={(row) => row.id}
          columns={[
            { key: "name", header: "이름", render: (row) => row.name },
            { key: "owner", header: "방장", hideBelow: "sm", render: (row) => row.ownerNickname },
            {
              key: "members",
              header: "인원",
              align: "right",
              render: (row) => <span className="tabular-nums">{row.memberCount}</span>,
            },
            {
              key: "join",
              header: "가입",
              hideBelow: "sm",
              render: (row) => (
                <Badge tone="muted">{row.openJoin ? "공개" : "초대만"}</Badge>
              ),
            },
            {
              key: "actions",
              header: "",
              align: "right",
              render: (row) => (
                <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => takedown(row)}>
                  내리기
                </Button>
              ),
            },
          ]}
        />
      ) : null}

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
