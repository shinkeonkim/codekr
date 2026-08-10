import { AuthProvider } from "@/features/auth";
import { ToastProvider, ToastViewport } from "@/shared/ui";
import { AppShell } from "@/widgets/app-shell";
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
      {/*
        본문이 짧은 화면에서도 Footer 가 바닥에 붙어 있게 세로 flex 로 잡고 main 이
        남는 공간을 차지하게 한다 (#74). 페이지마다 여백을 넣어 맞추면 다음 페이지에서
        또 어긋나므로 레이아웃에서 한 번만 해결한다.
        100vh 가 아니라 100dvh 인 이유: 모바일 주소창 때문에 vh 는 실제 높이와 어긋난다.
      */}
      <body className="flex min-h-[100dvh] flex-col bg-surface text-ink">
        <ToastProvider>
          <AuthProvider>
            {/* 헤더·본문 폭·푸터는 AppShell 한 곳에서 정한다 (#131). */}
            <AppShell>{children}</AppShell>
          </AuthProvider>
          {/* 화면을 넘어가도 살아남아야 하므로 라우트 바깥, 최상위에 둔다 (#112). */}
          <ToastViewport />
        </ToastProvider>
      </body>
    </html>
  );
}
