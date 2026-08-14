"use client";

import { Card, CardTitle } from "@/shared/ui";
import { useState } from "react";

/**
 * 프로필 배지를 붙여 넣는 자리 (#475).
 *
 * **이것이 없으면 아무도 안 쓴다.** 배지가 있다는 사실도, 주소의 모양도 알 길이 없기
 * 때문이다 — 기능이 아니라 이 한 조각이 배지를 쓰이게 한다.
 *
 * **설정 화면에 있다** (#547). 프로필은 남이 보러 오는 자리이고 이것은 내가 한 번
 * 복사해 가는 도구다 — 자기 프로필에만 보이는 칸을 두면 **내가 보는 프로필과 남이
 * 보는 프로필이 달라져서**, 내 프로필이 어떻게 보이는지 확인할 수 없다.
 */
export function BadgeSnippet({ handle }: { handle: string }) {
  const [copied, setCopied] = useState<string | null>(null);
  const origin = typeof window === "undefined" ? "" : window.location.origin;
  const badgeUrl = `${origin}/badge/${encodeURIComponent(handle)}.svg`;
  const profileUrl = `${origin}/users/${encodeURIComponent(handle)}`;

  const snippets = [
    {
      key: "markdown",
      label: "마크다운",
      text: `[![코드.kr](${badgeUrl})](${profileUrl})`,
    },
    {
      key: "html",
      label: "HTML",
      text: `<a href="${profileUrl}"><img src="${badgeUrl}" alt="코드.kr 프로필"></a>`,
    },
    {
      // GitHub README 는 라이트·다크가 함께 있다. <img> 안에서는 보는 쪽의 설정이
      // 그림까지 닿지 않으므로, 주소를 둘 두고 <picture> 가 고르게 한다.
      key: "github",
      label: "GitHub (라이트·다크)",
      text:
        `<picture>\n` +
        `  <source media="(prefers-color-scheme: dark)" srcset="${badgeUrl}?theme=dark">\n` +
        `  <img src="${badgeUrl}" alt="코드.kr 프로필">\n` +
        `</picture>`,
    },
  ];

  const copy = async (key: string, text: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(key);
  };

  return (
    <Card className="space-y-3 p-5">
      <CardTitle>프로필 배지</CardTitle>
      <p className="text-sm text-ink-muted">
        README·블로그에 붙이면 지금 푼 문제 수와 점수가 그려집니다. 10분마다
        새로 그립니다.
      </p>
      {/* 붙이기 전에 무엇이 붙는지 보여 준다. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={badgeUrl}
        alt="내 프로필 배지"
        width={420}
        height={120}
        className="max-w-full"
      />
      <ul className="space-y-2">
        {snippets.map((snippet) => (
          <li key={snippet.key} className="space-y-1">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-ink-muted">
                {snippet.label}
              </span>
              <button
                type="button"
                onClick={() => copy(snippet.key, snippet.text)}
                className="text-xs text-brand hover:underline"
              >
                {copied === snippet.key ? "복사했습니다" : "복사"}
              </button>
            </div>
            <pre className="overflow-x-auto rounded-md bg-surface-muted p-3 text-xs">
              <code>{snippet.text}</code>
            </pre>
          </li>
        ))}
      </ul>
    </Card>
  );
}
