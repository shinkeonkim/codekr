"use client";

import { UserLink } from "@/entities/user";
import { NotificationBell } from "./NotificationBell";
import { useAuth } from "@/features/auth";
import { BrandWordmark } from "@/shared/ui";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Button } from "@/shared/ui";
import { NAV_ITEMS, activeHref } from "./nav";

export function SiteHeader() {
  const { user, isAdmin, loading, signOut } = useAuth();
  const pathname = usePathname();
  // 활성 판정은 nav.ts 가 한다 — 접두사 비교만으로는 두 항목이 함께 켜진다 (#182).
  const active = activeHref(pathname);

  return (
    <header className="sticky top-0 z-header border-b border-border bg-surface/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
        {/* 워드마크 하나로 둔다 (#261) — "드" 안에 이미 `</>` 가 있어 심벌을 나란히
            놓으면 같은 표시가 두 번 나온다. */}
        <Link href="/" className="flex shrink-0 items-center">
          <BrandWordmark height={28} />
        </Link>

        <nav className="flex items-center gap-1 text-sm">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active === item.href ? "page" : undefined}
              className={`rounded-lg px-3 py-1.5 transition ${
                active === item.href ? "bg-surface-muted text-ink" : "text-ink-muted hover:text-ink"
              }`}
            >
              {item.label}
            </Link>
          ))}
          {isAdmin ? (
            <Link
              // 진입점은 하나다. 어느 구획으로 갈지는 어드민 첫 화면이 역할에 맞춰 보여준다 (#131).
              href="/admin"
              aria-current={pathname.startsWith("/admin") ? "page" : undefined}
              className={`rounded-lg px-3 py-1.5 transition ${
                pathname.startsWith("/admin") ? "bg-surface-muted text-ink" : "text-ink-muted hover:text-ink"
              }`}
            >
              어드민
            </Link>
          ) : null}
        </nav>

        <div className="ml-auto flex items-center gap-2 text-sm">
          {loading ? null : user ? (
            <>
              <span className="text-ink-muted">
                <UserLink nickname={user.nickname} />
                {isAdmin ? " (관리자)" : ""}
              </span>
              <NotificationBell />
              <Link href="/settings">
                <Button variant="ghost">설정</Button>
              </Link>
              <Button variant="ghost" onClick={signOut}>
                로그아웃
              </Button>
            </>
          ) : (
            <>
              <Link href="/login">
                <Button variant="ghost">로그인</Button>
              </Link>
              <Link href="/signup">
                <Button>회원가입</Button>
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
