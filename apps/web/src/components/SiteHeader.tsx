"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { Button } from "./ui";

const NAV_ITEMS = [
  { href: "/problems", label: "문제" },
  { href: "/submissions/explore", label: "전체 제출" },
  { href: "/submissions", label: "내 제출" },
];

export function SiteHeader() {
  const { user, isAdmin, loading, signOut } = useAuth();
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-20 border-b border-border bg-surface/90 backdrop-blur">
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
              href="/admin/problems"
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
                {user.nickname}
                {isAdmin ? " (관리자)" : ""}
              </span>
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
