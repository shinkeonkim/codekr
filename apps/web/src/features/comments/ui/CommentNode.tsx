"use client";

import { MAX_VISUAL_DEPTH } from "@/entities/post";
import type { Comment } from "@/entities/post";
import { Avatar } from "@/entities/user";
import { formatDateTime } from "@/shared/lib";
import { Button, Card, Markdown, Textarea } from "@/shared/ui";
import { useState } from "react";
import { CommentForm } from "./CommentForm";

/**
 * 댓글 한 줄 (#138) 과 제자리 편집 (#211).
 *
 * **지우고 다시 쓰지 않게 한다.** 오타 하나를 고치려고 지우면 달려 있던 답글이
 * "삭제된 댓글" 아래에 남는다 — 지우는 것과 고치는 것은 남는 결과가 다르다.
 *
 * 별도 화면으로 나가지 않는 이유: 스레드에서 위치를 잃는다.
 */
export function CommentNode({
  comment,
  depth,
  replyTo,
  canReply,
  onReplyTo,
  onSubmit,
  onRemove,
  onEdit,
}: {
  comment: Comment;
  depth: number;
  replyTo: number | null;
  canReply: boolean;
  onReplyTo: (id: number | null) => void;
  onSubmit: (body: string, parentId?: number) => void;
  onRemove: (id: number) => void;
  onEdit: (id: number, body: string) => Promise<boolean>;
}) {
  // 이 깊이를 넘으면 더 들여쓰지 않는다. 화면이 밀려나면 글을 읽을 수 없다.
  const indent = Math.min(depth, MAX_VISUAL_DEPTH);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(comment.body ?? "");

  const startEditing = () => {
    // 취소하면 원래 내용으로 돌아와야 하므로, 열 때마다 지금 값을 다시 담는다.
    setDraft(comment.body ?? "");
    setEditing(true);
  };

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
              {/*
                **작성 시각이 주(主)다** (#211). 스레드에서는 글이 언제 쓰였는지가 순서를
                말해 준다. 수정 시각은 곁들이되, 마우스를 올리면 정확한 시각이 나온다.
              */}
              <span>{formatDateTime(comment.createdAt)}</span>
              {comment.edited ? (
                <span title={comment.editedAt ? `${formatDateTime(comment.editedAt)} 수정` : undefined}>
                  · 수정됨
                  {comment.editedAt ? ` (${formatDateTime(comment.editedAt)})` : ""}
                </span>
              ) : null}
            </div>

            {editing ? (
              <div className="space-y-2">
                <Textarea rows={3} value={draft} onChange={(event) => setDraft(event.target.value)} />
                <div className="flex gap-2">
                  <Button
                    className="px-3 py-1 text-xs"
                    disabled={!draft.trim()}
                    onClick={async () => {
                      if (await onEdit(comment.id, draft)) setEditing(false);
                    }}
                  >
                    저장
                  </Button>
                  {/* 취소하면 원래 내용으로 돌아온다 — 다시 열 때 지금 값을 담는다. */}
                  <Button variant="ghost" className="px-3 py-1 text-xs" onClick={() => setEditing(false)}>
                    취소
                  </Button>
                </div>
              </div>
            ) : (
              <Markdown source={comment.body ?? ""} />
            )}

            {editing ? null : (
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
                {/*
                  **`editable` 과 `deletable` 은 다른 조건이다.** 어드민은 남의 댓글을
                  지울 수는 있어도 고칠 수는 없다 — 서버가 그렇게 막고 있고, 화면도
                  그 규칙과 같아야 한다.
                */}
                {comment.editable ? (
                  <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={startEditing}>
                    수정
                  </Button>
                ) : null}
                {comment.deletable ? (
                  <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => onRemove(comment.id)}>
                    삭제
                  </Button>
                ) : null}
              </div>
            )}
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
          onEdit={onEdit}
        />
      ))}
    </div>
  );
}
