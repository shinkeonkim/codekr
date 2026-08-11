"use client";

import { notificationApi } from "@/entities/notification";
import { useAuth } from "@/features/auth";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

/** 미읽음 수를 다시 확인하는 주기. 실시간이 필요한 정보가 아니다. */
const POLL_INTERVAL_MS = 60_000;

/**
 * 헤더의 알림 종 (#106).
 *
 * 목록 전체가 아니라 **숫자만** 가져온다. 헤더는 모든 화면에 있으므로 여기서 무거운
 * 조회를 하면 사이트 전체가 느려진다.
 */
export function NotificationBell() {
  const { user } = useAuth();
  const pathname = usePathname();
  const [state, setState] = useState<{ userId: number | null; unread: number }>({
    userId: null,
    unread: 0,
  });

  // 사용자가 바뀌면 앞사람의 숫자를 그대로 두지 않는다.
  // 렌더 중에 맞추는 방식이라 이펙트로 인한 추가 렌더가 생기지 않는다.
  const userId = user?.id ?? null;
  if (state.userId !== userId) setState({ userId, unread: 0 });

  useEffect(() => {
    if (!user) return;

    let cancelled = false;
    const load = () => {
      notificationApi
        .unreadCount()
        .then(({ unreadCount }) => {
          if (!cancelled) setState({ userId: user.id, unread: unreadCount });
        })
        .catch(() => undefined);
    };

    load();
    const timer = setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
    // 알림 화면을 오갈 때 숫자가 바로 맞도록 경로도 의존성에 둔다.
  }, [user, pathname]);

  if (!user) return null;
  const unread = state.unread;

  return (
    <Link
      href="/notifications"
      className="relative rounded-lg px-3 py-1.5 text-sm text-ink-muted transition hover:text-ink"
      aria-label={unread > 0 ? `알림 ${unread}건` : "알림"}
    >
      알림
      {unread > 0 ? (
        <span className="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-brand px-1 text-center text-[10px] font-bold leading-4 text-brand-ink">
          {unread > 99 ? "99+" : unread}
        </span>
      ) : null}
    </Link>
  );
}
