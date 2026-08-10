"use client";

import { postApi } from "@/entities/post";
import type { PostDetail } from "@/entities/post";
import { Avatar } from "@/entities/user";
import { CommentTree } from "@/features/comments";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Badge, Button, Card, EmptyState, Markdown, useToast } from "@/shared/ui";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useEffect, useState } from "react";

export function PostDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <Detail id={Number(id)} />;
}

function Detail({ id }: { id: number }) {
  const router = useRouter();
  const toast = useToast();
  const [post, setPost] = useState<PostDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    postApi
      .detail(id)
      .then(setPost)
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "글을 불러오지 못했습니다."),
      );
  }, [id]);

  if (error) return <EmptyState title={error} />;
  if (!post) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  const remove = async () => {
    if (!confirm("이 글을 삭제할까요?")) return;
    try {
      await postApi.remove(id);
      toast.success("글을 삭제했습니다.");
      router.push("/posts");
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "삭제하지 못했습니다.");
    }
  };

  const { summary } = post;

  return (
    <article className="mx-auto max-w-3xl space-y-5">
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={summary.board === "NOTICE" ? "info" : "muted"}>{summary.boardLabel}</Badge>
          <h1 className="text-2xl font-bold text-ink">{summary.title}</h1>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-ink-muted">
          <Avatar nickname={summary.authorNickname} avatarUrl={summary.authorAvatarUrl} size="sm" />
          <span>{summary.authorNickname}</span>
          <span>·</span>
          <span>{formatDateTime(summary.createdAt)}</span>
          {summary.edited ? <span>· 수정됨 {formatDateTime(summary.updatedAt)}</span> : null}
        </div>
      </header>

      <Card className="p-5">
        {/*
          마크다운을 React 엘리먼트로 직접 만든다 (#137).
          HTML 문자열을 만들지 않으므로 사용자가 쓴 <script> 는 글자로만 보인다.
        */}
        <Markdown source={post.body} />
      </Card>

      <div className="flex flex-wrap gap-2">
        {post.editable ? (
          <Link href={`/posts/${id}/edit`}>
            <Button variant="secondary">수정</Button>
          </Link>
        ) : null}
        {/* 운영자는 고칠 수 없지만 내릴 수는 있다 — 두 권한이 다르다. */}
        {post.deletable ? (
          <Button variant="danger" onClick={remove}>
            삭제
          </Button>
        ) : null}
      </div>

      <CommentTree postId={id} />
    </article>
  );
}
