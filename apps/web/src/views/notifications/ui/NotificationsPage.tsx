"use client";

import { notificationApi } from "@/entities/notification";
import type { NotificationCategoryOption } from "@/entities/notification";
import { userApi } from "@/entities/user";
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
  /** 빈 문자열이 전체 탭이다. 전체가 첫 번째이고 기본값이다 (#135). */
  const [category, setCategory] = useState("");
  const [result, setResult] = useState<Page<Notification> | null>(null);
  const [options, setOptions] = useState<NotificationCategoryOption[]>([]);
  const [unread, setUnread] = useState<Record<string, number>>({});

  const load = useCallback(() => {
    notificationApi
      .list({
        page,
        size: 20,
        unreadOnly: unreadOnly ? "true" : "false",
        // 탭과 "안 읽은 것만" 은 함께 걸린다 — 이 탭에서 안 읽은 것.
        category: category || undefined,
      })
      .then(setResult)
      .catch(() => toast.error("알림을 불러오지 못했습니다."));
    notificationApi
      .unreadCount()
      .then(({ byCategory }) => setUnread(byCategory))
      .catch(() => undefined);
  }, [page, unreadOnly, category, toast]);

  useEffect(load, [load]);

  // 탭 목록은 **서버가 내려주는 카테고리 옵션**에서 만든다. 화면이 하드코딩하지 않는다.
  useEffect(() => {
    userApi
      .settings()
      .then((settings) => setOptions(settings.notificationCategories))
      .catch(() => setOptions([]));
  }, []);

  const readAll = async () => {
    await notificationApi.markAllRead(category || undefined);
    toast.success(category ? "이 탭의 알림을 읽음으로 표시했습니다." : "모두 읽음으로 표시했습니다.");
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
          {/* 문구가 무엇을 읽는지 말한다. "모두" 가 탭 안인지 밖인지 헷갈리면 안 된다. */}
          <Button variant="secondary" onClick={readAll}>
            {category ? "이 탭 모두 읽음" : "모두 읽음"}
          </Button>
        </div>
      </header>

      <nav aria-label="알림 분류" className="flex flex-wrap gap-1.5">
        {[{ category: "", label: "전체" }, ...options].map((option) => {
          const active = category === option.category;
          const count = option.category ? (unread[option.category] ?? 0) : 0;
          return (
            <button
              key={option.category || "ALL"}
              type="button"
              aria-current={active ? "page" : undefined}
              onClick={() => {
                setCategory(option.category);
                setPage(0);
              }}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs transition ${
                active
                  ? "border-brand bg-brand/12 font-medium text-ink"
                  : "border-border text-ink-muted hover:text-ink"
              }`}
            >
              {option.label}
              {count > 0 ? (
                <span className="rounded-full bg-brand px-1.5 text-[10px] font-bold text-brand-ink">
                  {count}
                </span>
              ) : null}
            </button>
          );
        })}
      </nav>

      {result && result.content.length === 0 ? (
        <EmptyState
          mascot="cat"
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
