"use client";

import type { FeedbackStatus, SiteFeedback } from "@/entities/feedback";
import { formatDateTime } from "@/shared/lib";
import { Badge, Button, Card, Input } from "@/shared/ui";
import { useState } from "react";

/**
 * 신고·제안 한 건 (#603).
 *
 * **넣은 화면 주소를 보인다.** 어드민이 재현하려면 "어디에서" 가 있어야 하고,
 * 그것이 없으면 본문만 읽고 짐작해야 한다.
 */
export function FeedbackCard({
  feedback,
  onResolve,
}: {
  feedback: SiteFeedback;
  onResolve: (id: number, status: FeedbackStatus, resolution: string) => void;
}) {
  const [resolution, setResolution] = useState("");
  const open = feedback.status === "OPEN";

  return (
    <Card className="space-y-2 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={open ? "warn" : "muted"}>{feedback.statusLabel}</Badge>
        <Badge tone="muted">{feedback.kindLabel}</Badge>
        <span className="text-sm text-ink">{feedback.reporterNickname}</span>
        <span className="ml-auto text-xs text-ink-muted">{formatDateTime(feedback.createdAt)}</span>
      </div>

      {feedback.pageUrl ? (
        <p className="break-all text-xs text-ink-muted">{feedback.pageUrl}</p>
      ) : null}

      <p className="whitespace-pre-wrap break-words text-sm text-ink">{feedback.body}</p>

      {feedback.resolution ? (
        <p className="border-t border-border pt-2 text-xs text-ink-muted">
          처리 내용: <span className="text-ink">{feedback.resolution}</span>
        </p>
      ) : null}

      {open ? (
        <div className="space-y-2 border-t border-border pt-2">
          {/*
            **반영하지 않을 때 이유가 중요하다.** 이유 없는 거절은 넣은 사람에게
            "읽지 않았다" 와 구분되지 않고, 그러면 다음 제안이 오지 않는다.
          */}
          <Input
            value={resolution}
            placeholder="처리 내용 (넣은 사람에게 알림으로 갑니다)"
            onChange={(event) => setResolution(event.target.value)}
          />
          <div className="flex flex-wrap gap-2">
            <Button className="px-3 py-1 text-xs" onClick={() => onResolve(feedback.id, "ACCEPTED", resolution)}>
              반영했음
            </Button>
            <Button
              variant="secondary"
              className="px-3 py-1 text-xs"
              onClick={() => onResolve(feedback.id, "REJECTED", resolution)}
            >
              반영하지 않음
            </Button>
          </div>
        </div>
      ) : null}
    </Card>
  );
}
