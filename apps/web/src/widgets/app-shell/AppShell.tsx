"use client";

import { SiteHeader } from "@/widgets/site-header";
import { SiteFooter } from "./SiteFooter";
import { usePathname } from "next/navigation";
import { PendingTermsBanner } from "@/features/terms";
import type { ReactNode } from "react";
import { SHELL_WIDTH } from "@/shared/ui/pageWidth";

/**
 * 사용자 화면의 껍데기.
 *
 * **어드민은 자기 껍데기를 쓴다** (#179). 여기서 아무것도 두르지 않고 그대로 내보낸다 —
 * 사용자 헤더·푸터가 어드민 위에 남아 있으면 지금 어디에 있는지 흐려진다.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (pathname.startsWith("/admin")) return <>{children}</>;

  return (
    <>
      <SiteHeader />
      <main className={`${SHELL_WIDTH} flex-1 space-y-4 py-8`}>
        {/* 개정된 약관이 있으면 여기서 알린다 (#235). 없으면 아무것도 그리지 않는다. */}
        <PendingTermsBanner />
        {children}
      </main>
      <SiteFooter />
    </>
  );
}
