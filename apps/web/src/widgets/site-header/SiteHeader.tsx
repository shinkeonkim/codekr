"use client";

import { UserLink } from "@/entities/user";
import { NotificationBell } from "./NotificationBell";
import { useAuth } from "@/features/auth";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Button } from "@/shared/ui";

const NAV_ITEMS = [
  { href: "/problems", label: "문제" },
  { href: "/submissions/explore", label: "전체 제출" },
  { href: "/contests", label: "대회" },
  { href: "/ranking", label: "랭킹" },
  { href: "/submissions", label: "내 제출" },
];

export function SiteHeader() {
  const { user, isAdmin, loading, signOut } = useAuth();
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-header border-b border-border bg-surface/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
        <Link href="/" className="text-lg font-bold tracking-tight text-ink">
          코드<span className="text-brand">.kr</span>
        </Link>

        <nav className="flex items-center gap-1 text-sm">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`rounded-lg px-3 py-1.5 transition ${
                pathname.startsWith(item.href) ? "bg-surface-muted text-ink" : "text-ink-muted hover:text-ink"
              }`}
            >
              {item.label}
            </Link>
          ))}
          {isAdmin ? (
            <Link
              // 진입점은 하나다. 어느 구획으로 갈지는 어드민 첫 화면이 역할에 맞춰 보여준다 (#131).
              href="/admin"
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
