"use client";

import { UserLink } from "@/entities/user";
import { NotificationBell } from "./NotificationBell";
import { SideNav } from "./SideNav";
import { useAuth } from "@/features/auth";
import { BrandWordmark } from "@/shared/ui";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useState } from "react";

import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/shared/ui";
import { NAV_ITEMS, activeHref } from "./nav";

/**
 * 사이트 헤더 (#182, #261, #289).
 *
 * **좁은 화면에서는 감추고 드로어로 연다.** 항목이 일곱 개라 휴대폰 폭에 다 넣을
 * 방법이 없다 — 전에는 한 줄을 고집하다가 글자가 세로로 쌓이고 오른쪽 끝이 화면
 * 밖으로 나갔다(모든 화면이 가로로 550px 였다).
 *
 * **경계는 `md`(768) 가 아니라 `lg`(1024) 다.** 처음에 `md` 로 두었더니 정확히 768px
 * 에서 다시 무너졌다 — 로그인하면 `어드민`·`알림`·닉네임·`설정`·`로그아웃` 이 더 붙어
 * 열 개가 넘는다. **비로그인으로만 재면 놓치는 자리다.**
 *
 * 알림 벨은 드로어 밖에 남긴다. **읽지 않은 수는 열어 보기 전에 보여야** 알림이다.
 */
export function SiteHeader() {
  const { user, isAdmin, loading, signOut } = useAuth();
  const pathname = usePathname();
  // 활성 판정은 nav.ts 가 한다 — 접두사 비교만으로는 두 항목이 함께 켜진다 (#182).
  const active = activeHref(pathname);
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = useCallback(() => setMenuOpen(false), []);

  return (
    <>
      <header className="sticky top-0 z-header border-b border-border bg-surface/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4 lg:gap-6">
        {/* 워드마크 하나로 둔다 (#261) — "드" 안에 이미 `</>` 가 있어 심벌을 나란히
            놓으면 같은 표시가 두 번 나온다. */}
        <Link href="/" className="flex shrink-0 items-center">
          <BrandWordmark height={28} />
        </Link>

        {/* 넓은 화면의 가로 내비. 좁으면 드로어가 같은 목록을 보여준다. */}
        <nav className="hidden items-center gap-1 text-sm lg:flex">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active === item.href ? "page" : undefined}
              className={`whitespace-nowrap rounded-lg px-2.5 py-1.5 transition lg:px-3 ${
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
              className={`whitespace-nowrap rounded-lg px-2.5 py-1.5 transition lg:px-3 ${
                pathname.startsWith("/admin")
                  ? "bg-surface-muted text-ink"
                  : "text-ink-muted hover:text-ink"
              }`}
            >
              어드민
            </Link>
          ) : null}
        </nav>

        <div className="ml-auto flex items-center gap-2 text-sm">
          {/* 알림은 좁은 화면에서도 헤더에 남는다. */}
          {loading || !user ? null : <NotificationBell />}

          <div className="hidden items-center gap-2 lg:flex">
            {loading ? null : user ? (
              /*
                사용자 영역을 메뉴 하나로 모은다 (#291 4단계).

                전에는 닉네임·설정·로그아웃을 나란히 늘어놓아서 항목이 늘 때마다 헤더가
                길어졌다. **키보드와 포커스는 Radix 가 맡는다** — 바깥 클릭·Esc·위아래
                이동·닫을 때 원래 자리로 되돌리기를 직접 만들면 대개 절반만 맞고,
                그 절반이 키보드로만 쓰는 사람에게는 전부다.
              */
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost">
                    {user.nickname}
                    {isAdmin ? " (관리자)" : ""}
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuLabel>{user.nickname}</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link href={`/users/${encodeURIComponent(user.handle)}`}>내 프로필</Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link href="/settings">설정</Link>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={() => signOut()}>로그아웃</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <>
                <Button asChild variant="ghost"><Link href="/login">로그인</Link></Button>
                <Button asChild><Link href="/signup">회원가입</Link></Button>
              </>
            )}
          </div>

          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            aria-label="메뉴 열기"
            aria-expanded={menuOpen}
            className="rounded-lg p-2 text-ink transition hover:bg-surface-muted lg:hidden"
          >
            {/* 선 세 개. 아이콘 묶음을 들이지 않고 그린다 — 이 하나 때문에 의존성이 늘 이유가 없다. */}
            <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true" fill="none">
              <path
                d="M3 5h14M3 10h14M3 15h14"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </div>
      </div>
      </header>

      {/*
        **드로어를 헤더 밖에 둔다** (#289). 헤더에는 `backdrop-blur` 가 걸려 있는데,
        `backdrop-filter` 는 자손 `position: fixed` 의 기준 상자를 자기 자신으로
        바꾼다 — 안에 두었더니 화면 전체를 덮어야 할 패널이 **헤더 높이 안에** 갇혔다.
      */}
      <SideNav open={menuOpen} onClose={closeMenu} />
    </>
  );
}
