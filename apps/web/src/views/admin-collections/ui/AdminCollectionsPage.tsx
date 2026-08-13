"use client";

import { adminCollectionApi } from "@/entities/collection";
import type { AdminCollectionDetail, AdminCollectionRow } from "@/entities/collection";
import type { Page } from "@/shared/api";
import {
  Badge,
  Button,
  Card,
  CardTitle,
  ConfirmDialog,
  EmptyState,
  Field,
  Input,
  Pagination,
  Select,
  Table,
  useToast,
} from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 문제집 관리 (#393).
 *
 * **문제집만 빠져 있었다.** 대회(#335)·글과 댓글(#336)에는 어드민이 보는 곳이 있는데,
 * 사용자가 만드는 것 중 문제집만 볼 방법이 없었다 — 공개 목록이 없을 뿐 링크로는
 * 남에게 보여지고 있었다.
 *
 * **남에게 보여지는 것만 보인다.** 비공개는 목록에도 상세에도 오지 않는다 —
 * 남이 혼자 쓰는 목록을 들여다보는 것은 이 화면이 푸는 문제가 아니다.
 */
export function AdminCollectionsPage() {
  const toast = useToast();
  const [visibility, setVisibility] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<AdminCollectionRow> | null>(null);
  const [detail, setDetail] = useState<AdminCollectionDetail | null>(null);
  const [reason, setReason] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    adminCollectionApi
      .list({ visibility: visibility || undefined, page, size: 20 })
      .then(setResult)
      .catch(() => setResult(null));
  }, [visibility, page, reloadKey]);

  const takedown = async (id: number) => {
    if (!reason.trim()) {
      // 사유는 **관리 기록에 남고 주인에게도 간다** (#225, #208).
      toast.error("사유를 먼저 적어 주세요.");
      return;
    }
    try {
      await adminCollectionApi.takedown(id, reason.trim());
      toast.success("공개 목록에서 내렸습니다.");
      setReason("");
      setDetail(null);
      setReloadKey((key) => key + 1);
    } catch {
      toast.error("내리지 못했습니다.");
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-2">
        <div className="w-56">
          <Field label="공개 범위">
          <Select value={visibility} onChange={(event) => { setVisibility(event.target.value); setPage(0); }}>
            <option value="">전체</option>
            <option value="PUBLIC">누구나 보기</option>
            <option value="UNLISTED">링크가 있는 사람만</option>
          </Select>
          </Field>
        </div>
        <div className="min-w-56 flex-1">
          <Field label="내리는 사유">
          <Input
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="주인에게 그대로 전해집니다"
          />
          </Field>
        </div>
      </div>

      {result && result.content.length > 0 ? (
        <>
          <Table
            columns={[
              {
                key: "name",
                header: "문제집",
                // 무엇이 문제인지는 내용을 봐야 안다 — 이름을 누르면 담긴 문제가 열린다.
                render: (row) => (
                  <button type="button" className="text-brand hover:underline" onClick={() => open(row.id)}>
                    {row.name}
                  </button>
                ),
              },
              { key: "owner", header: "만든 사람", render: (row) => row.ownerNickname },
              {
                key: "visibility",
                header: "범위",
                render: (row) => (
                  <Badge tone={row.visibility === "PUBLIC" ? "info" : "muted"}>{row.visibilityLabel}</Badge>
                ),
              },
              { key: "count", header: "문제", width: "w-20", render: (row) => `${row.problemCount}개` },
              {
                key: "action",
                header: "",
                hideBelow: "sm",
                render: (row) =>
                  row.visibility === "PUBLIC" ? (
                    <ConfirmDialog
                      title={`'${row.name}' 을 공개 목록에서 내릴까요?`}
                      description="지우지 않습니다. 비공개로 되돌리면 주인은 그대로 갖고 있고, 사유가 함께 전해집니다."
                      confirmLabel="내리기"
                      onConfirm={() => takedown(row.id)}
                      trigger={<Button variant="danger" className="px-2 py-0.5 text-xs">내리기</Button>}
                    />
                  ) : (
                    // 링크 공유는 목록에 오른 적이 없으므로 내릴 것이 없다.
                    <span className="text-xs text-ink-muted">—</span>
                  ),
              },
            ]}
            rows={result.content}
            rowKey={(row) => row.id}
          />
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onChange={setPage}
          />
        </>
      ) : (
        <EmptyState title="남에게 보여지는 문제집이 없습니다." mascot="study" />
      )}

      {detail ? (
        <Card className="space-y-2.5 p-5">
          <CardTitle>{detail.name}</CardTitle>
          <p className="text-xs text-ink-muted">
            {detail.ownerNickname} · {detail.visibilityLabel} · {detail.problems.length}문제
          </p>
          {detail.description ? <p className="text-sm text-ink-muted">{detail.description}</p> : null}
          <ul className="space-y-1 text-sm">
            {detail.problems.map((problem) => (
              <li key={problem.problemId}>
                <a href={`/problems/${problem.slug}`} className="text-brand hover:underline">
                  {problem.title}
                </a>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
    </div>
  );

  function open(id: number) {
    adminCollectionApi.detail(id).then(setDetail).catch(() => toast.error("불러오지 못했습니다."));
  }
}
