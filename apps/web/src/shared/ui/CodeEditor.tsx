"use client";

import { useTheme } from "@/shared/theme";
import Editor from "@monaco-editor/react";

interface Props {
  language: string;
  value: string;
  onChange: (value: string) => void;
  height?: number;
  /**
   * 읽기만 하는 자리인가 (#457).
   *
   * 문제가 정한 파일(인터페이스·하네스)을 **보여 주되 고치지는 못하게** 한다.
   * 감추지 않는 이유는 그것을 읽는 것이 문제의 절반이기 때문이다.
   */
  readOnly?: boolean;
}

/**
 * Monaco 래퍼. 테마와 옵션을 한 곳에 모아 화면마다 다르게 보이는 일을 막는다.
 *
 * **에디터는 자기 색을 직접 정한다** — CSS 토큰을 쓰지 않으므로 고른 테마를 따로
 * 알려 줘야 한다 (#206). 전에는 OS 설정만 봐서, 사이트를 어둡게 골라도 **에디터만
 * 밝게** 남았다. 코드를 읽는 화면에서 그 어긋남이 가장 크게 보인다.
 */
export function CodeEditor({
  language,
  value,
  onChange,
  height = 480,
  readOnly = false,
}: Props) {
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
          readOnly,
          fontSize: 13,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          tabSize: 4,
          automaticLayout: true,
          padding: { top: 12, bottom: 12 },
        }}
        loading={
          <div className="p-4 text-sm text-ink-muted">
            에디터를 불러오는 중…
          </div>
        }
      />
    </div>
  );
}
