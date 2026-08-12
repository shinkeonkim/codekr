"use client";

import { useAuth } from "@/features/auth";
import { Card, EmptyState } from "@/shared/ui";
import { visibleNav } from "@/widgets/admin-nav";
import Link from "next/link";

/**
 * 어드민 첫 화면 (#131).
 *
 * 헤더의 어드민 링크가 곧바로 문제 관리로 갔었다. 문제를 만지지 않는 역할
 * (대회 관리자 등)에게는 **쓸 수 없는 화면이 첫 화면**이 된다.
 */
export function AdminHomePage() {
  const { user } = useAuth();
  const items = visibleNav(user?.roles ?? []);

  if (items.length === 0) {
    return <EmptyState mascot="forbidden" title="접근할 수 있는 어드민 구획이 없습니다." />;
  }

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">어드민</h1>
        <p className="mt-1 text-sm text-ink-muted">권한이 있는 구획만 보입니다.</p>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((item) => (
          <Link key={item.href} href={item.href}>
            <Card className="h-full p-5 transition hover:border-brand/50">
              <p className="font-medium text-ink">{item.label}</p>
              <p className="mt-1 text-xs text-ink-muted">{item.description}</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
