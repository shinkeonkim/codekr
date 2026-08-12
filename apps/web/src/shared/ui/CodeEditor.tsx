"use client";

import { useTheme } from "@/features/theme";
import Editor from "@monaco-editor/react";

interface Props {
  language: string;
  value: string;
  onChange: (value: string) => void;
  height?: number;
}

/**
 * Monaco 래퍼. 테마와 옵션을 한 곳에 모아 화면마다 다르게 보이는 일을 막는다.
 *
 * **에디터는 자기 색을 직접 정한다** — CSS 토큰을 쓰지 않으므로 고른 테마를 따로
 * 알려 줘야 한다 (#206). 전에는 OS 설정만 봐서, 사이트를 어둡게 골라도 **에디터만
 * 밝게** 남았다. 코드를 읽는 화면에서 그 어긋남이 가장 크게 보인다.
 */
export function CodeEditor({ language, value, onChange, height = 480 }: Props) {
  const { resolved } = useTheme();

  return (
    <div className="overflow-hidden rounded-card border border-border">
      <Editor
        height={height}
        language={language}
        value={value}
        theme={resolved === "dark" ? "vs-dark" : "light"}
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
