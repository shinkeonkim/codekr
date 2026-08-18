"use client";

import { feedbackApi, FEEDBACK_KINDS } from "@/entities/feedback";
import type { FeedbackKind, SiteFeedback } from "@/entities/feedback";
import { RequireAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import type { Page } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Badge, Button, Card, EmptyState, Field, PAGE_WIDTH, Select, Textarea, useToast } from "@/shared/ui";
import { useCallback, useEffect, useState } from "react";

/**
 * 사이트 신고·제안 (#603).
 *
 * **전에는 푸터가 GitHub 이슈 목록으로 나갔다.** 계정이 있어야 하고, 로그인해야 하고,
 * 공개된 곳에 쓰는 일이다 — 코딩 테스트를 풀러 온 사람에게 요구할 일이 아니고,
 * 사실상 안 받는 것에 가까웠다.
 *
 * **내가 넣은 것을 같이 보인다.** 어디로 갔는지 볼 수 없으면 다시 넣지 않는다.
 * 처리되면 알림도 간다(#106).
 */
export function FeedbackPage() {
  return (
    <RequireAuth>
      <Feedback />
    </RequireAuth>
  );
}

function Feedback() {
  const toast = useToast();
  const [kind, setKind] = useState<FeedbackKind>("BUG");
  const [body, setBody] = useState("");
  const [saving, setSaving] = useState(false);
  const [mine, setMine] = useState<Page<SiteFeedback> | null>(null);

  const load = useCallback(() => {
    feedbackApi
      .mine({ page: 0, size: 20 })
      .then(setMine)
      .catch(() => setMine(null));
  }, []);

  useEffect(load, [load]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      /*
        **어느 화면에서 왔는지 같이 보낸다.** 재현하려면 "어디에서" 가 있어야 하는데,
        사람에게 주소를 적게 하면 대부분 비워 둔다. 이 페이지 자체의 주소는 뜻이
        없으므로 눌러 온 곳을 쓴다.
      */
      const from = document.referrer || undefined;
      await feedbackApi.submit({ kind, body, pageUrl: from });
      toast.success("보냈습니다. 처리되면 알려 드립니다.");
      setBody("");
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "보내지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={`${PAGE_WIDTH.reading} space-y-5`}>
      <header>
        <h1 className="text-2xl font-bold text-ink">신고·제안</h1>
        <p className="mt-1 text-sm text-ink-muted">
          안 되는 것, 있었으면 하는 것을 알려 주세요. 문제 지문이나 테스트케이스의 오류는
          그 문제 화면의 <strong>오류 신고</strong>로 보내 주시면 더 빨리 고쳐집니다.
        </p>
      </header>

      <Card className="p-5">
        <form className="space-y-3" onSubmit={submit}>
          <Field label="무엇을 말하려는지">
            <Select value={kind} onChange={(event) => setKind(event.target.value as FeedbackKind)}>
              {FEEDBACK_KINDS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="내용">
            <Textarea
              rows={6}
              value={body}
              onChange={(event) => setBody(event.target.value)}
              placeholder="어디에서 무엇을 하다가 그랬는지 적어 주시면 더 빨리 고칠 수 있습니다."
              required
            />
          </Field>
          <Button type="submit" disabled={saving || !body.trim()}>
            {saving ? "보내는 중…" : "보내기"}
          </Button>
        </form>
      </Card>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-ink">보낸 것</h2>
        {mine && mine.content.length === 0 ? (
          <EmptyState title="아직 보낸 것이 없습니다." />
        ) : null}
        {mine?.content.map((feedback) => (
          <Card key={feedback.id} className="space-y-1 p-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={feedback.status === "OPEN" ? "warn" : "muted"}>{feedback.statusLabel}</Badge>
              <Badge tone="muted">{feedback.kindLabel}</Badge>
              <span className="ml-auto text-xs text-ink-muted">{formatDateTime(feedback.createdAt)}</span>
            </div>
            <p className="whitespace-pre-wrap break-words text-sm text-ink">{feedback.body}</p>
            {feedback.resolution ? (
              <p className="border-t border-border pt-2 text-xs text-ink-muted">
                처리 내용: <span className="text-ink">{feedback.resolution}</span>
              </p>
            ) : null}
          </Card>
        ))}
      </section>
    </div>
  );
}
