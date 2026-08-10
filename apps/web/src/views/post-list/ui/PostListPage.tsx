"use client";

import { postApi } from "@/entities/post";
import type { BoardOption, PostSummary } from "@/entities/post";
import { Avatar } from "@/entities/user";
import type { Page } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Badge, Button, Card, EmptyState, Field, Input, Pagination, Select } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

export function PostListPage() {
  const [boards, setBoards] = useState<BoardOption[]>([]);
  const [board, setBoard] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<PostSummary> | null>(null);

  useEffect(() => {
    // 게시판 목록은 서버가 알려준다. 쓸 수 있는지도 함께 온다.
    postApi.boards().then(setBoards).catch(() => setBoards([]));
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      postApi
        .list({ board: board || undefined, q: keyword || undefined, page, size: 20 })
        .then(setResult)
        .catch(() => setResult(null));
    }, 200);
    return () => clearTimeout(timer);
  }, [board, keyword, page]);

  const writable = boards.filter((it) => it.writable);

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-ink">게시판</h1>
        {/* 쓸 수 있는 곳이 하나도 없으면(비로그인) 버튼을 보이지 않는다. */}
        {writable.length > 0 ? (
          <Link href="/posts/new" className="ml-auto">
            <Button>글쓰기</Button>
          </Link>
        ) : null}
      </header>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="게시판">
          <Select
            value={board}
            onChange={(event) => {
              setBoard(event.target.value);
              setPage(0);
            }}
          >
            <option value="">전체</option>
            {boards.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="검색">
          <Input
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
            placeholder="제목"
          />
        </Field>
      </div>

      {result && result.content.length === 0 ? (
        <EmptyState
          title={keyword || board ? "조건에 맞는 글이 없습니다." : "아직 글이 없습니다."}
          description={keyword || board ? "검색어나 게시판을 바꿔 보세요." : "첫 글을 써 보세요."}
        />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((post) => (
          <Link key={post.id} href={`/posts/${post.id}`} className="block">
            <Card className="flex flex-wrap items-center gap-3 p-4 transition hover:border-brand/50">
              <Badge tone={post.board === "NOTICE" ? "info" : "muted"}>{post.boardLabel}</Badge>
              <span className="min-w-0 flex-1 truncate font-medium text-ink">
                {post.title}
                {post.edited ? <span className="ml-1 text-xs text-ink-muted">(수정됨)</span> : null}
              </span>
              <span className="flex items-center gap-1.5 text-xs text-ink-muted">
                <Avatar nickname={post.authorNickname} avatarUrl={post.authorAvatarUrl} size="sm" />
                {post.authorNickname}
              </span>
              {/* 답이 달렸는지가 목록에서 보여야 질문 글이 쓸모가 있다 (#138). */}
              {post.commentCount > 0 ? (
                <span className="text-xs text-ink-muted">댓글 {post.commentCount}</span>
              ) : null}
              <span className="text-xs text-ink-muted">{formatDateTime(post.createdAt)}</span>
            </Card>
          </Link>
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
