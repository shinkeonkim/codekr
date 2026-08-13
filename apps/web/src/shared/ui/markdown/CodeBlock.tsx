"use client";

import { useTheme } from "@/shared/theme";
import { Highlight, themes } from "prism-react-renderer";

/**
 * 마크다운의 코드 블록 (#384).
 *
 * **HTML 문자열을 만들지 않는다.** `Markdown`(#137)이 `dangerouslySetInnerHTML` 을 쓰지
 * 않기로 한 것이 이 저장소에서 가장 오래된 안전 규칙이고, 하이라이팅을 넣자고 그것을
 * 깰 수는 없다 — 여기서 새는 구멍은 게시글·댓글·질문에 그대로 이어진다.
 *
 * 그래서 **토큰을 돌려주는** 하이라이터를 골랐다. `prism-react-renderer` 는 Prism 의
 * 토큰 배열을 React 엘리먼트로 그린다. Shiki 는 Monaco 와 색이 같아진다는 값이 있지만
 * 비동기 초기화가 필요하고, 그동안 코드가 회색으로 남았다가 칠해진다.
 *
 * **모르는 언어는 그대로 둔다.** 언어를 안 적은 블록이 대부분이고, 그것이 깨지면
 * 하이라이팅이 없는 편이 낫다.
 */
export function CodeBlock({ code, language }: { code: string; language?: string }) {
  const { resolved } = useTheme();
  const prismLanguage = language ? PRISM_LANGUAGES[language.toLowerCase()] : undefined;

  if (!prismLanguage) {
    return (
      <pre
        className="overflow-x-auto rounded-lg bg-surface-muted p-3 text-xs"
        data-language={language || undefined}
      >
        <code>{code}</code>
      </pre>
    );
  }

  return (
    <Highlight
      code={code.replace(/\n$/, "")}
      language={prismLanguage}
      // 에디터(#206)와 같은 규칙 — 사이트를 어둡게 골랐으면 코드도 어둡다.
      theme={resolved === "dark" ? themes.vsDark : themes.github}
    >
      {({ tokens, getLineProps, getTokenProps }) => (
        <pre
          className="overflow-x-auto rounded-lg bg-surface-muted p-3 text-xs"
          data-language={language}
        >
          <code>
            {tokens.map((line, index) => {
              const { key: _lineKey, ...lineProps } = getLineProps({ line });
              return (
                <div key={index} {...lineProps}>
                  {line.map((token, tokenIndex) => {
                    const { key: _tokenKey, ...tokenProps } = getTokenProps({ token });
                    return <span key={tokenIndex} {...tokenProps} />;
                  })}
                </div>
              );
            })}
          </code>
        </pre>
      )}
    </Highlight>
  );
}

/**
 * 마크다운에 적히는 이름 → Prism 이 아는 이름.
 *
 * **여기 없는 이름은 칠하지 않는다.** 아무 문자열이나 넘기면 Prism 이 문법을 못 찾고
 * 그때 블록이 통째로 비는 경우가 있다 — 안 칠해지는 것보다 나쁘다.
 *
 * 지금 지원하는 런타임 열다섯의 언어를 덮고, 마크다운에서 흔히 적는 별칭도 받는다.
 */
const PRISM_LANGUAGES: Record<string, string> = {
  python: "python",
  py: "python",
  javascript: "javascript",
  js: "javascript",
  typescript: "typescript",
  ts: "typescript",
  tsx: "tsx",
  jsx: "jsx",
  c: "c",
  "c++": "cpp",
  cpp: "cpp",
  java: "java",
  kotlin: "kotlin",
  kt: "kotlin",
  go: "go",
  golang: "go",
  rust: "rust",
  rs: "rust",
  ruby: "ruby",
  rb: "ruby",
  "c#": "csharp",
  csharp: "csharp",
  cs: "csharp",
  sql: "sql",
  bash: "bash",
  sh: "bash",
  shell: "bash",
  json: "json",
  yaml: "yaml",
  yml: "yaml",
  html: "markup",
  xml: "markup",
  css: "css",
  diff: "diff",
};
