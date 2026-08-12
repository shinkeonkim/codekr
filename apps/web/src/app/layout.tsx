import { AuthProvider } from "@/features/auth";
import { SITE_DESCRIPTION, SITE_NAME, SITE_ORIGIN } from "@/shared/config/site";
import { ThemeScript } from "@/shared/theme";
import { ToastProvider, ToastViewport } from "@/shared/ui";
import { AppShell } from "@/widgets/app-shell";
import type { Metadata } from "next";
import type { ReactNode } from "react";

import "./globals.css";

export const metadata: Metadata = {
  /*
    공유 카드의 그림·주소는 **절대 주소**여야 한다 (#277). 기준이 없으면 Next 가
    `http://localhost:3000` 을 바탕으로 만들고, 카카오톡 서버는 그 주소로 그림을
    받으러 갈 수 없다 — **그림이 있는데 안 뜨는** 상태가 그것이었다.

    값은 퓨니코드다 (ADR-0009). `URL` 이 어차피 정규화하고, 구글은 둘을 같게 본다.
  */
  metadataBase: new URL(SITE_ORIGIN),
  title: `${SITE_NAME} — 코딩 테스트 문제 풀이 플랫폼`,
  description: SITE_DESCRIPTION,
  openGraph: {
    type: "website",
    // 카드에 뜨는 사이트 이름. **여기만 한글이다** — 주소는 퓨니코드로 뜬다.
    siteName: SITE_NAME,
    locale: "ko_KR",
    title: `${SITE_NAME} — 코딩 테스트 문제 풀이 플랫폼`,
    description: SITE_DESCRIPTION,
    url: "/",
  },
  twitter: {
    // 큰 카드. `summary` 는 그림이 우표만 하게 뜬다 — 준비해 둔 그림을 쓰는 뜻이 없어진다.
    card: "summary_large_image",
    title: `${SITE_NAME} — 코딩 테스트 문제 풀이 플랫폼`,
    description: SITE_DESCRIPTION,
  },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        {/*
          고른 테마를 **첫 그림 전에** 바른다 (#206). 리액트가 붙은 뒤에 바르면
          밝은 화면이 한 번 번쩍이고, 그것이 이 이슈에서 가장 까다로운 부분이었다.
          `<html>` 의 속성이 서버 HTML 과 달라지므로 경고를 끈다 — 다른 것이 정상이다.
        */}
        <ThemeScript />
      </head>
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
