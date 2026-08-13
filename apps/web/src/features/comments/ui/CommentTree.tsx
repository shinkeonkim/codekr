"use client";

import { postApi } from "@/entities/post";
import type { Comment } from "@/entities/post";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Alert, useToast } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";
import { CommentForm } from "./CommentForm";
import { CommentNode } from "./CommentNode";

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

  /**
   * 제자리 편집 (#211).
   *
   * **성공 여부를 돌려준다** — 실패했는데 편집창이 닫히면 쓰던 내용이 사라진다.
   */
  const edit = async (id: number, body: string): Promise<boolean> => {
    try {
      setComments(await postApi.updateComment(id, body));
      return true;
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "수정하지 못했습니다.");
      return false;
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
            onEdit={edit}
          />
        ))}
      </div>
    </section>
  );
}

function count(comments: Comment[]): number {
  return comments.reduce(
    (sum, comment) => sum + (comment.deleted ? 0 : 1) + count(comment.children),
    0,
  );
}
