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
          <Button asChild><Link href="/posts/new" className="ml-auto">글쓰기</Link></Button>
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
          mascot="laptop"
          title={keyword || board ? "조건에 맞는 글이 없습니다." : "아직 글이 없습니다."}
          description={keyword || board ? "검색어나 게시판을 바꿔 보세요." : "첫 글을 써 보세요."}
        />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((post) => (
          <Link key={post.id} href={`/posts/${post.id}`} className="block">
            {/*
              **좁으면 두 줄, 넓으면 한 줄** (#462).

              전에는 다섯 가지가 한 줄에 `flex` 로 늘어서 있고 제목만 `flex-1 truncate`
              였다. 남는 공간을 제목이 받으므로 **모자라면 제목만 줄어든다** — 목록에서
              가장 중요한 것이 가장 먼저 사라졌다. 훑는 사람은 제목을 읽고 들어갈지
              정한다.

              **감추지 않는다.** 표는 좁을 때 열을 감추지만(#193), 카드는 세로로 늘릴 수
              있다 — 작성자·댓글·날짜를 아랫줄로 내리면 다 보인다.

              넓은 화면은 그대로 한 줄이다. 조밀함은 거기서 값이 크고(#79), 폭에 따라
              모양이 달라지는 것보다 **좁은 쪽만 고치는** 편이 낫다.
            */}
            <Card className="flex flex-col gap-2 p-4 transition hover:border-brand/50 sm:flex-row sm:flex-wrap sm:items-center sm:gap-3">
              <span className="flex min-w-0 items-center gap-3 sm:flex-1">
                <Badge tone={post.board === "NOTICE" ? "info" : "muted"}>{post.boardLabel}</Badge>
                {/*
                  좁은 화면에서는 두 줄까지 보인다. 그보다 길면 자른다 — 카드가 계속
                  높아지면 한 화면에 보이는 글 수가 줄어든다.
                */}
                <span className="min-w-0 flex-1 font-medium text-ink line-clamp-2 sm:truncate">
                  {post.title}
                  {post.edited ? <span className="ml-1 text-xs text-ink-muted">(수정됨)</span> : null}
                </span>
              </span>
              <span className="flex items-center gap-3 text-xs text-ink-muted">
                <span className="flex items-center gap-1.5">
                  <Avatar nickname={post.authorNickname} avatarUrl={post.authorAvatarUrl} size="sm" />
                  {post.authorNickname}
                </span>
                {/* 답이 달렸는지가 목록에서 보여야 질문 글이 쓸모가 있다 (#138). */}
                {post.commentCount > 0 ? <span>댓글 {post.commentCount}</span> : null}
                <span>{formatDateTime(post.createdAt)}</span>
              </span>
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
