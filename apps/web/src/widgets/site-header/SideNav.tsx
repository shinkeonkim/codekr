"use client";

import { useAuth } from "@/features/auth";
import { Button } from "@/shared/ui";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { NAV_ITEMS, activeHref } from "./nav";

/**
 * 좁은 화면의 사이드 내비 (#289).
 *
 * 헤더에 항목이 일곱 개다. 휴대폰 폭에서는 **넣을 방법이 없어서** 글자가 세로로 쌓이고
 * 오른쪽 끝이 화면 밖으로 나갔다. 감추고 여기서 연다.
 *
 * 목록은 `NAV_ITEMS` 를 그대로 쓴다 (#182). 여기에 따로 적으면 항목이 늘 때 한쪽만
 * 고쳐지고, 그 사실은 **좁은 화면으로 본 사람만** 알게 된다.
 */
export function SideNav({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user, isAdmin, loading, signOut } = useAuth();
  const pathname = usePathname();
  const active = activeHref(pathname);
  const panelRef = useRef<HTMLDivElement>(null);

  // 화면을 옮기면 닫는다. 열어 둔 채로 뒤 내용만 바뀌면 무엇을 눌렀는지 알 수 없다.
  // biome-ignore lint/correctness/useExhaustiveDependencies: 경로가 바뀔 때만 닫는다.
  useEffect(() => {
    onClose();
  }, [pathname]);

  useEffect(() => {
    if (!open) return;

    // Esc 는 "지금 연 것을 되돌린다" 의 관례다. 없으면 바깥을 정확히 눌러야만 닫힌다.
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);

    // 열려 있는 동안 뒤가 스크롤되면, 닫았을 때 보던 자리가 아니다.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    // 열자마자 키보드 초점을 안으로 넣는다. 그러지 않으면 Tab 이 뒤 본문을 훑는다.
    panelRef.current?.focus();

    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-header md:hidden">
      {/* 바깥을 눌러 닫는다. 키보드 사용자에게는 Esc 가 같은 일을 하므로 초점을 주지 않는다. */}
      <button
        type="button"
        aria-label="메뉴 닫기"
        onClick={onClose}
        className="absolute inset-0 bg-ink/40 backdrop-blur-sm"
      />

      <div
        ref={panelRef}
        // biome-ignore lint/a11y/noNoninteractiveTabindex: 열자마자 초점을 받을 곳이 필요하다.
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="메뉴"
        className="absolute inset-y-0 right-0 flex w-72 max-w-[85vw] flex-col overflow-y-auto border-l border-border bg-surface p-4 shadow-xl outline-none"
      >
        <nav className="flex flex-col gap-1">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active === item.href ? "page" : undefined}
              className={`rounded-lg px-3 py-2.5 text-sm transition ${
                active === item.href ? "bg-surface-muted text-ink" : "text-ink-muted hover:text-ink"
              }`}
            >
              {item.label}
            </Link>
          ))}
          {isAdmin ? (
            <Link
              href="/admin"
              aria-current={pathname.startsWith("/admin") ? "page" : undefined}
              className={`rounded-lg px-3 py-2.5 text-sm transition ${
                pathname.startsWith("/admin")
                  ? "bg-surface-muted text-ink"
                  : "text-ink-muted hover:text-ink"
              }`}
            >
              어드민
            </Link>
          ) : null}
        </nav>

        <div className="mt-4 flex flex-col gap-2 border-t border-border pt-4">
          {loading ? null : user ? (
            <>
              <p className="px-3 text-sm text-ink-muted">
                {user.nickname}
                {isAdmin ? " (관리자)" : ""}
              </p>
              <Link href="/settings">
                <Button variant="secondary" className="w-full">
                  설정
                </Button>
              </Link>
              <Button variant="ghost" onClick={signOut} className="w-full">
                로그아웃
              </Button>
            </>
          ) : (
            <>
              <Link href="/login">
                <Button variant="secondary" className="w-full">
                  로그인
                </Button>
              </Link>
              <Link href="/signup">
                <Button className="w-full">회원가입</Button>
              </Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
