"use client";

import { postApi } from "@/entities/post";
import type { Comment } from "@/entities/post";
import { lastId, mergeComments } from "../model/merge";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Alert, Button, useToast } from "@/shared/ui";
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
  // 서버가 세는 값이다 (#213). 받은 트리를 세면 잘라 내리기 시작한 순간 틀린다.
  const [total, setTotal] = useState(0);
  const [remainingTop, setRemainingTop] = useState(0);

  /** 새로 받은 트리를 이미 그린 것 위에 겹치지 않게 얹는다. */
  const apply = (tree: { comments: Comment[]; totalCount: number; remainingTop: number }) => {
    setComments((current) => mergeComments(current ?? [], tree.comments));
    setTotal(tree.totalCount);
    setRemainingTop(tree.remainingTop);
  };

  useEffect(() => {
    // 알림·링크로 들어왔으면 그 자리가 보이도록 조상을 펴서 받는다 (#212).
    const anchorId = Number(window.location.hash.replace("#comment-", ""));
    postApi
      .comments(postId, Number.isFinite(anchorId) && anchorId > 0 ? { around: anchorId } : undefined)
      .then((loaded) => {
        setComments(loaded.comments);
        setTotal(loaded.totalCount);
        setRemainingTop(loaded.remainingTop);
        /*
          알림이 준 자리로 옮긴다 (#212).

          댓글은 이 컴포넌트가 받아 온 뒤에 그려지므로, 주소에 앵커가 있어도 브라우저가
          그릴 때는 아직 그 자리가 없다. 받아 온 다음 프레임에 직접 옮긴다.
        */
        const anchor = window.location.hash.slice(1);
        if (!anchor.startsWith("comment-")) return;
        requestAnimationFrame(() => {
          document.getElementById(anchor)?.scrollIntoView({ block: "center" });
        });
      })
      .catch(() => setComments([]));
  }, [postId]);

  const submit = async (body: string, parentId?: number) => {
    try {
      apply(await postApi.addComment(postId, { parentId, body }));
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
      apply(await postApi.updateComment(id, body));
      return true;
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "수정하지 못했습니다.");
      return false;
    }
  };

  const remove = async (id: number) => {
    if (!confirm("이 댓글을 삭제할까요?")) return;
    try {
      apply(await postApi.removeComment(id));
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "삭제하지 못했습니다.");
    }
  };

  /** 한 부모의 답글을 조금씩 편다 — 한 번에 다 펴면 접은 이유가 사라진다. */
  const loadChildren = async (commentId: number, after?: number) => {
    try {
      const tree = await postApi.commentChildren(commentId, after);
      setComments((current) => graft(current ?? [], commentId, tree.comments, tree.remainingTop));
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "답글을 더 불러오지 못했습니다.");
    }
  };

  const loadMoreTop = async () => {
    try {
      apply(await postApi.comments(postId, { after: lastId(comments ?? []) }));
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "댓글을 더 불러오지 못했습니다.");
    }
  };

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
            onLoadChildren={loadChildren}
          />
        ))}

        {remainingTop > 0 ? (
          <Button variant="secondary" className="w-full text-xs" onClick={loadMoreTop}>
            댓글 {remainingTop}개 더 보기
          </Button>
        ) : null}
      </div>
    </section>
  );
}

/** 이어받은 답글을 그 부모 자리에 끼워 넣는다. 트리의 다른 곳은 건드리지 않는다. */
function graft(
  comments: Comment[],
  parentId: number,
  children: Comment[],
  remaining: number,
): Comment[] {
  return comments.map((comment) => {
    if (comment.id === parentId) {
      return {
        ...comment,
        children: mergeComments(comment.children, children),
        remainingChildren: remaining,
      };
    }
    return { ...comment, children: graft(comment.children, parentId, children, remaining) };
  });
}
