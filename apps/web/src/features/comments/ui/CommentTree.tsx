"use client";

import { MAX_VISUAL_DEPTH, postApi } from "@/entities/post";
import type { Comment } from "@/entities/post";
import { Avatar } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Alert, Button, Card, Markdown, Textarea, useToast } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 댓글 트리 (#138).
 *
 * **깊이 제한은 화면에만 있다.** 저장에는 없다 — "대댓글까지만" 으로 막으면 세 번째
 * 발언부터 누구에게 하는 말인지 사라진다. 다만 깊어질수록 들여쓰기가 화면을 밀어내므로
 * 일정 깊이를 넘으면 더 들여쓰지 않는다.
 */
export function CommentTree({ postId }: { postId: number }) {
  const { user } = useAuth();
  const toast = useToast();
  const [comments, setComments] = useState<Comment[] | null>(null);
  const [replyTo, setReplyTo] = useState<number | null>(null);

  useEffect(() => {
    postApi.comments(postId).then(setComments).catch(() => setComments([]));
  }, [postId]);

  const submit = async (body: string, parentId?: number) => {
    try {
      setComments(await postApi.addComment(postId, { parentId, body }));
      setReplyTo(null);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "댓글을 남기지 못했습니다.");
    }
  };

  const remove = async (id: number) => {
    if (!confirm("이 댓글을 삭제할까요?")) return;
    try {
      setComments(await postApi.removeComment(id));
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "삭제하지 못했습니다.");
    }
  };

  const total = comments ? count(comments) : 0;

  return (
    <section className="space-y-3">
      <h2 className="text-sm font-semibold text-ink">댓글 {total}개</h2>

      {/* 비로그인에는 입력창 대신 안내를 보여준다 (#113 과 같은 결). */}
      {user ? (
        <CommentForm onSubmit={(body) => submit(body)} placeholder="댓글을 남겨 보세요" />
      ) : (
        <Alert>
          댓글을 쓰려면 <Link href="/login" className="underline">로그인</Link>이 필요합니다.
        </Alert>
      )}

      <div className="space-y-2">
        {comments?.map((comment) => (
          <CommentNode
            key={comment.id}
            comment={comment}
            depth={0}
            replyTo={replyTo}
            canReply={Boolean(user)}
            onReplyTo={setReplyTo}
            onSubmit={submit}
            onRemove={remove}
          />
        ))}
      </div>
    </section>
  );
}

function CommentNode({
  comment,
  depth,
  replyTo,
  canReply,
  onReplyTo,
  onSubmit,
  onRemove,
}: {
  comment: Comment;
  depth: number;
  replyTo: number | null;
  canReply: boolean;
  onReplyTo: (id: number | null) => void;
  onSubmit: (body: string, parentId?: number) => void;
  onRemove: (id: number) => void;
}) {
  // 이 깊이를 넘으면 더 들여쓰지 않는다. 화면이 밀려나면 글을 읽을 수 없다.
  const indent = Math.min(depth, MAX_VISUAL_DEPTH);

  return (
    <div style={{ marginLeft: indent * 16 }} className="space-y-2">
      <Card className="space-y-2 p-4">
        {comment.deleted ? (
          // 지운 사람이 누구인지 남길 이유가 없다. 자리만 남긴다.
          <p className="text-xs text-ink-muted">삭제된 댓글입니다.</p>
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2 text-xs text-ink-muted">
              <Avatar nickname={comment.authorNickname ?? "?"} avatarUrl={comment.authorAvatarUrl} size="sm" />
              <span className="text-ink">{comment.authorNickname}</span>
              <span>{formatDateTime(comment.createdAt)}</span>
              {comment.edited ? <span>· 수정됨</span> : null}
            </div>
            <Markdown source={comment.body ?? ""} />
            <div className="flex flex-wrap gap-2">
              {canReply ? (
                <Button
                  variant="ghost"
                  className="px-2 py-0.5 text-xs"
                  onClick={() => onReplyTo(replyTo === comment.id ? null : comment.id)}
                >
                  {replyTo === comment.id ? "취소" : "답글"}
                </Button>
              ) : null}
              {comment.deletable ? (
                <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => onRemove(comment.id)}>
                  삭제
                </Button>
              ) : null}
            </div>
          </>
        )}
      </Card>

      {replyTo === comment.id ? (
        <div style={{ marginLeft: 16 }}>
          <CommentForm onSubmit={(body) => onSubmit(body, comment.id)} placeholder="답글" />
        </div>
      ) : null}

      {comment.children.map((child) => (
        <CommentNode
          key={child.id}
          comment={child}
          depth={depth + 1}
          replyTo={replyTo}
          canReply={canReply}
          onReplyTo={onReplyTo}
          onSubmit={onSubmit}
          onRemove={onRemove}
        />
      ))}
    </div>
  );
}

function CommentForm({
  onSubmit,
  placeholder,
}: {
  onSubmit: (body: string) => void;
  placeholder: string;
}) {
  const [body, setBody] = useState("");

  return (
    <form
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault();
        if (!body.trim()) return;
        onSubmit(body);
        setBody("");
      }}
    >
      <Textarea
        rows={3}
        value={body}
        onChange={(event) => setBody(event.target.value)}
        placeholder={placeholder}
      />
      <Button type="submit" className="px-3 py-1 text-xs" disabled={!body.trim()}>
        남기기
      </Button>
    </form>
  );
}

function count(comments: Comment[]): number {
  return comments.reduce(
    (sum, comment) => sum + (comment.deleted ? 0 : 1) + count(comment.children),
    0,
  );
}
