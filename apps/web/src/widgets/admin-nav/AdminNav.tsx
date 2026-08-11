"use client";

import { useAuth } from "@/features/auth";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { visibleNav } from "./model";

/**
 * 어드민 사이드바 (#131).
 *
 * 전에는 어드민으로 가는 길이 헤더 링크 하나(`/admin/problems`)뿐이라,
 * **큐 모니터링에 가려면 주소를 직접 쳐야 했다.**
 */
export function AdminNav() {
  const pathname = usePathname();
  const { user } = useAuth();
  const items = visibleNav(user?.roles ?? []);

  return (
    <nav aria-label="어드민 메뉴">
      {/* 좁은 화면에서는 가로로 스크롤되는 줄로 접는다. 사이드바를 세로로 두면 본문이 밀린다. */}
      <ul className="flex gap-1.5 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0">
        {items.map((item) => {
          const active = pathname.startsWith(item.href);
          return (
            <li key={item.href} className="shrink-0 lg:shrink">
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`block rounded-lg px-3 py-2 text-sm transition ${
                  active
                    ? "bg-brand/12 font-medium text-ink"
                    : "text-ink-muted hover:bg-surface-muted hover:text-ink"
                }`}
              >
                {item.label}
                <span className="hidden text-xs text-ink-muted lg:block">{item.description}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
