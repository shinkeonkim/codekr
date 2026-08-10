import { AuthProvider } from "@/features/auth";
import { SiteHeader } from "@/widgets/site-header";
import type { Metadata } from "next";
import type { ReactNode } from "react";

import "./globals.css";

export const metadata: Metadata = {
  title: "코드.kr — 코딩 테스트 문제 풀이 플랫폼",
  description:
    "알고리즘, 자료구조, SQL, 네트워크, 운영체제, 시스템 설계까지. 실시간 채점 과정을 보며 문제를 풉니다.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body className="min-h-screen bg-surface text-ink">
        <AuthProvider>
          <SiteHeader />
          <main className="mx-auto w-full max-w-6xl px-4 py-8">{children}</main>
          <footer className="border-t border-border py-6 text-center text-xs text-ink-muted">
            코드.kr · 오픈소스 코딩 테스트 플랫폼
          </footer>
        </AuthProvider>
      </body>
    </html>
  );
}
