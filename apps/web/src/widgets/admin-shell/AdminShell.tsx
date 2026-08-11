"use client";

import { useAuth } from "@/features/auth";
import { Button } from "@/shared/ui";
import Link from "next/link";
import type { ReactNode } from "react";
import { AdminNav } from "@/widgets/admin-nav";

/**
 * 어드민 콘솔의 껍데기 (#179).
 *
 * **사용자 사이트의 헤더·푸터를 쓰지 않는다.** 처음에는 "헤더를 유지하면 돌아가기 쉽다"고
 * 판단했지만(#131), 실제로 열어 보니 사용자 내비가 어드민 화면 위에 그대로 남아 지금
 * 어디에 있는지 흐려졌다. 돌아가는 길은 링크 하나면 충분하다.
 *
 * 폭도 여기서 한 번만 정한다 — 상단바는 `max-w-6xl`, 본문은 `max-w-[1600px]` 로 서로
 * 어긋나 있었다.
 */
export function AdminShell({ children }: { children: ReactNode }) {
  const { user, signOut } = useAuth();

  return (
    <div className="flex min-h-[100dvh] flex-col">
      <header className="sticky top-0 z-header border-b border-border bg-surface-muted/80 backdrop-blur">
        <div className="mx-auto flex h-14 w-full max-w-[1600px] items-center gap-4 px-6">
          <Link href="/admin" className="flex items-baseline gap-2">
            <span className="text-base font-bold tracking-tight text-ink">
              코드<span className="text-brand">.kr</span>
            </span>
            {/* 사용자 화면과 확실히 구분되는 표식. 색이 아니라 글자로 둔다 — 색만으로
                구분하면 색을 구별하지 못하는 사람에게는 같은 화면이다. */}
            <span className="rounded bg-brand/15 px-1.5 py-0.5 text-xs font-medium text-brand">
              어드민
            </span>
          </Link>

          <div className="ml-auto flex items-center gap-2 text-sm">
            <Link href="/" className="text-ink-muted transition hover:text-ink">
              ← 서비스로
            </Link>
            {user ? (
              <>
                <span aria-hidden className="text-border">|</span>
                <span className="hidden text-ink-muted sm:inline">{user.nickname}</span>
                <Button variant="ghost" onClick={signOut}>
                  로그아웃
                </Button>
              </>
            ) : null}
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-[1600px] flex-1 flex-col gap-6 px-6 py-6 lg:flex-row lg:gap-8">
        {/* 사이드바를 선으로 끊는다 — 배경만으로는 본문과 같은 평면으로 읽힌다. */}
        <div className="lg:w-56 lg:shrink-0 lg:border-r lg:border-border lg:pr-4">
          <AdminNav />
        </div>
        <main className="min-w-0 flex-1">{children}</main>
      </div>
    </div>
  );
}
