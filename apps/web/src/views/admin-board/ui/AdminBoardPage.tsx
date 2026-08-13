"use client";

import { adminBoardApi } from "@/entities/post";
import type { AdminCommentRow, AdminPostRow } from "@/entities/post";
import type { Page } from "@/shared/api";
import { Badge, Button, EmptyState, Field, Input, Pagination, Table, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

type Tab = "posts" | "comments";

/**
 * 게시판 관리 (#336).
 *
 * **"지울 수 있다" 와 "지울 것을 찾을 수 있다" 는 다르다.** 지금까지 운영자가 문제 있는
 * 글을 지우려면 그 글을 우연히 보고 있어야 했다 — 게시판이 셋이고 댓글은 글 안에
 * 들어가야만 보였다.
 *
 * **글과 댓글을 탭으로 나눈다.** 한 표에 섞으면 열의 뜻이 달라진다 (글은 제목, 댓글은
 * 본문 앞부분).
 */
export function AdminBoardPage() {
  const toast = useToast();
  const [tab, setTab] = useState<Tab>("posts");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [posts, setPosts] = useState<Page<AdminPostRow> | null>(null);
  const [comments, setComments] = useState<Page<AdminCommentRow> | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [reason, setReason] = useState("");

  useEffect(() => {
    const query = { authorNickname: keyword.trim() || undefined, page, size: 20 };
    if (tab === "posts") {
      adminBoardApi.posts(query).then(setPosts).catch(() => setPosts(null));
    } else {
      adminBoardApi.comments(query).then(setComments).catch(() => setComments(null));
    }
  }, [tab, keyword, page, reloadKey]);

  const remove = async (kind: Tab, id: number) => {
    if (!reason.trim()) {
      // 사유는 **관리 기록에 남는다** (#225) — 비운 채로 지우면 나중에 답할 수 없다.
      toast.error("사유를 먼저 적어 주세요.");
      return;
    }
    try {
      if (kind === "posts") await adminBoardApi.deletePost(id, reason.trim());
      else await adminBoardApi.deleteComment(id, reason.trim());
      toast.success("내렸습니다.");
      setReloadKey((key) => key + 1);
    } catch {
      toast.error("내리지 못했습니다.");
    }
  };

  const result = tab === "posts" ? posts : comments;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-ink">게시판 관리</h1>
        <p className="mt-1 text-sm text-ink-muted">
          최근에 올라온 글과 댓글을 한 곳에서 봅니다. 내린 사실은 관리 기록에 남습니다.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {(["posts", "comments"] as const).map((each) => (
          <Button
            key={each}
            variant={tab === each ? "primary" : "secondary"}
            className="px-3 py-1 text-xs"
            onClick={() => {
              setTab(each);
              setPage(0);
            }}
          >
            {each === "posts" ? "글" : "댓글"}
          </Button>
        ))}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="작성자">
          <Input
            placeholder="닉네임 일부"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field label="사유 (내릴 때 함께 기록됩니다)">
          <Input
            placeholder="예: 광고"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
      </div>

      {result && result.content.length === 0 ? <EmptyState title="해당하는 것이 없습니다." /> : null}

      {tab === "posts" && posts && posts.content.length > 0 ? (
        <Table
          rows={posts.content}
          rowKey={(row) => row.id}
          columns={[
            { key: "board", header: "게시판", render: (row) => <Badge tone="muted">{row.board}</Badge> },
            { key: "title", header: "제목", render: (row) => row.title },
            { key: "author", header: "작성자", hideBelow: "sm", render: (row) => row.authorNickname },
            {
              key: "comments",
              header: "댓글",
              align: "right",
              hideBelow: "sm",
              render: (row) => <span className="tabular-nums">{row.commentCount}</span>,
            },
            {
              key: "actions",
              header: "",
              align: "right",
              render: (row) => (
                <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => remove("posts", row.id)}>
                  내리기
                </Button>
              ),
            },
          ]}
        />
      ) : null}

      {tab === "comments" && comments && comments.content.length > 0 ? (
        <Table
          rows={comments.content}
          rowKey={(row) => row.id}
          columns={[
            { key: "excerpt", header: "내용", render: (row) => row.excerpt },
            { key: "post", header: "달린 글", hideBelow: "sm", render: (row) => row.postTitle },
            { key: "author", header: "작성자", hideBelow: "sm", render: (row) => row.authorNickname },
            {
              key: "actions",
              header: "",
              align: "right",
              render: (row) => (
                <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => remove("comments", row.id)}>
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
