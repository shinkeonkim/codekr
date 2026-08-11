"use client";

import { notificationApi } from "@/entities/notification";
import type { Notification } from "@/entities/notification";
import { RequireAuth } from "@/features/auth";
import type { Page } from "@/shared/api";
import { formatDateTime } from "@/shared/lib";
import { Badge, Button, Card, EmptyState, Pagination, useToast } from "@/shared/ui";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

export function NotificationsPage() {
  return (
    <RequireAuth>
      <NotificationList />
    </RequireAuth>
  );
}

function NotificationList() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [result, setResult] = useState<Page<Notification> | null>(null);

  const load = useCallback(() => {
    notificationApi
      .list({ page, size: 20, unreadOnly: unreadOnly ? "true" : "false" })
      .then(setResult)
      .catch(() => toast.error("알림을 불러오지 못했습니다."));
  }, [page, unreadOnly, toast]);

  useEffect(load, [load]);

  const readAll = async () => {
    await notificationApi.markAllRead();
    toast.success("모두 읽음으로 표시했습니다.");
    load();
  };

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-ink">알림</h1>
        <div className="ml-auto flex items-center gap-2">
          <Button
            variant={unreadOnly ? "primary" : "ghost"}
            onClick={() => {
              setUnreadOnly((previous) => !previous);
              setPage(0);
            }}
          >
            안 읽은 것만
          </Button>
          <Button variant="secondary" onClick={readAll}>
            모두 읽음
          </Button>
        </div>
      </header>

      {result && result.content.length === 0 ? (
        <EmptyState
          title={unreadOnly ? "안 읽은 알림이 없습니다." : "받은 알림이 없습니다."}
          description="채점 결과가 바뀌거나 대회 공지가 올라오면 여기에 표시됩니다."
        />
      ) : null}

      <ul className="space-y-2">
        {result?.content.map((notification) => (
          <li key={notification.id}>
            <NotificationRow notification={notification} onRead={load} />
          </li>
        ))}
      </ul>

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

function NotificationRow({ notification, onRead }: { notification: Notification; onRead: () => void }) {
  const markRead = () => {
    if (notification.read) return;
    void notificationApi.markRead(notification.id).then(onRead);
  };

  const content = (
    // 안 읽은 것은 왼쪽 띠로 구분한다. 색만으로 구분하지 않도록 '새 알림' 라벨도 둔다.
    <Card
      className={`px-5 py-3.5 transition hover:border-brand/40 ${
        notification.read ? "" : "border-l-2 border-l-brand"
      }`}
    >
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="info">{notification.categoryLabel}</Badge>
        <span className="font-medium text-ink">{notification.title}</span>
        {notification.read ? null : <span className="text-xs text-brand">새 알림</span>}
        <span className="ml-auto text-xs text-ink-muted">{formatDateTime(notification.createdAt)}</span>
      </div>
      {notification.body ? (
        <p className="mt-1 text-sm text-ink-muted">{notification.body}</p>
      ) : null}
    </Card>
  );

  // 갈 곳이 있으면 링크로, 없으면 읽음 처리만 하는 버튼으로.
  return notification.link ? (
    <Link href={notification.link} onClick={markRead} className="block">
      {content}
    </Link>
  ) : (
    <button type="button" onClick={markRead} className="block w-full text-left">
      {content}
    </button>
  );
}
