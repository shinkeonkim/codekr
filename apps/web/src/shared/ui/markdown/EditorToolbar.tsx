"use client";

import { Button } from "@/shared/ui/button";
import { codeFence, linePrefix, link, orderedList, wrap } from "./editorCommands";
import type { EditResult } from "./editorCommands";

type Command = (text: string, start: number, end: number) => EditResult;

/**
 * 편집기 도구 모음 (#388, #602).
 *
 * **`MarkdownEditor` 에서 떼어 냈다.** 버튼이 늘어나면 편집기 파일이 버튼 목록으로
 * 덮여 정작 중요한 것 — 미리보기가 실제 렌더러를 쓴다는 것 — 이 안 보인다.
 *
 * 처음에는 다섯 개였다. 질문 글에 실제로 자주 쓰이는 **링크·인용·번호 목록**이 없어서
 * 그것들을 더했다. **표는 넣지 않았다** — `Markdown` 이 아직 표를 그리지 못하므로
 * (#590) 버튼을 주면 눌러 놓고 결과가 글자로 나온다.
 */
const COMMANDS: readonly { readonly label: string; readonly run: Command }[] = [
  { label: "굵게", run: (t, s, e) => wrap(t, s, e, "**") },
  { label: "코드", run: (t, s, e) => wrap(t, s, e, "`") },
  { label: "코드 블록", run: (t, s, e) => codeFence(t, s, e) },
  { label: "제목", run: (t, s, e) => linePrefix(t, s, e, "## ") },
  { label: "목록", run: (t, s, e) => linePrefix(t, s, e, "- ") },
  { label: "번호 목록", run: orderedList },
  { label: "인용", run: (t, s, e) => linePrefix(t, s, e, "> ") },
  { label: "링크", run: link },
];

export function EditorToolbar({
  onCommand,
  onPickImage,
  uploading,
  preview,
  onTogglePreview,
}: {
  onCommand: (command: Command) => void;
  onPickImage: () => void;
  uploading: boolean;
  preview: boolean;
  onTogglePreview: () => void;
}) {
  return (
    /* 좁은 화면에서는 줄이 접힌다. 도구 모음이 가로로 넘치면 못 쓰는 버튼이 생긴다. */
    <div className="flex flex-wrap items-center gap-1">
      {COMMANDS.map((command) => (
        <ToolButton key={command.label} label={command.label} onClick={() => onCommand(command.run)} />
      ))}
      <ToolButton label={uploading ? "올리는 중…" : "이미지"} onClick={onPickImage} />
      {/* 미리보기는 글을 바꾸지 않으므로 다른 버튼들과 떼어 놓는다. */}
      <span className="ml-auto">
        <ToolButton label={preview ? "미리보기 닫기" : "미리보기"} onClick={onTogglePreview} />
      </span>
    </div>
  );
}

function ToolButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <Button type="button" variant="secondary" className="px-2 py-1 text-xs" onClick={onClick}>
      {label}
    </Button>
  );
}
