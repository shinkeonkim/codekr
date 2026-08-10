"use client";

import Editor from "@monaco-editor/react";

interface Props {
  language: string;
  value: string;
  onChange: (value: string) => void;
  height?: number;
}

/** Monaco 래퍼. 테마와 옵션을 한 곳에 모아 화면마다 다르게 보이는 일을 막는다. */
export function CodeEditor({ language, value, onChange, height = 480 }: Props) {
  const prefersDark =
    typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;

  return (
    <div className="overflow-hidden rounded-card border border-border">
      <Editor
        height={height}
        language={language}
        value={value}
        theme={prefersDark ? "vs-dark" : "light"}
        onChange={(next) => onChange(next ?? "")}
        options={{
          fontSize: 13,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          tabSize: 4,
          automaticLayout: true,
          padding: { top: 12, bottom: 12 },
        }}
        loading={<div className="p-4 text-sm text-ink-muted">에디터를 불러오는 중…</div>}
      />
    </div>
  );
}
