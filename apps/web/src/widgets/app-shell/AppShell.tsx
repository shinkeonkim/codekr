"use client";

import { SiteHeader } from "@/widgets/site-header";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

/**
 * 화면 껍데기 (#131).
 *
 * **본문 폭을 여기 한 곳에서 정한다.** 어드민은 넓은 표와 폼이 주라서 사용자 화면의
 * `max-w-6xl` 로는 좁다. 자식 레이아웃에서는 부모가 건 폭 제한을 풀 수 없으므로,
 * 폭을 정하는 자리를 위로 올렸다.
 *
 * 헤더는 어드민에서도 유지한다 — 알림함(#106)과 로그아웃이 거기 있고,
 * 사용자 화면으로 돌아가는 길이기도 하다.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const wide = pathname.startsWith("/admin");

  return (
    <>
      <SiteHeader />
      <main className={`mx-auto w-full flex-1 px-4 py-8 ${wide ? "max-w-[1600px]" : "max-w-6xl"}`}>
        {children}
      </main>
      <footer className="border-t border-border py-6 text-center text-xs text-ink-muted">
        코드.kr · 오픈소스 코딩 테스트 플랫폼
      </footer>
    </>
  );
}
