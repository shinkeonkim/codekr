"use client";

import { Button } from "@/shared/ui/button";
import { Textarea } from "@/shared/ui/textarea";
import { ApiError, request } from "@/shared/api";
import { useRef, useState } from "react";
import { Markdown } from "./Markdown";
import { codeFence, linePrefix, wrap } from "./editorCommands";
import type { EditResult } from "./editorCommands";

/**
 * 마크다운 편집기 (#388).
 *
 * **편집기 라이브러리를 들이지 않았다.** 들이면 그리는 쪽이 둘이 된다 — 편집기의
 * 미리보기와 우리 `Markdown`. **둘이 다르게 그리면 미리보기가 거짓말이 된다.**
 * 그리고 `Markdown` 은 `isSafeUrl` 로 링크를 거르는데(#137), 편집기 미리보기가 그것을
 * 안 거치면 **작성자만 다른 것을 본다.**
 *
 * 그래서 작성 칸은 그대로 두고 **버튼과 미리보기만 붙였다.** 이슈가 "가장 작고 위험이
 * 통째로 사라진다" 고 적은 길이다. 새 의존성이 없으므로 지연 로딩도 필요 없다.
 *
 * 이미지 첨부(#389)는 이 도구 모음에 붙는다.
 */
export function MarkdownEditor({
  value,
  onChange,
  rows = 14,
  placeholder,
}: {
  value: string;
  onChange: (next: string) => void;
  rows?: number;
  placeholder?: string;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  /**
   * 버튼을 누르면 **커서가 있던 자리로 돌아간다.**
   *
   * 값만 바꾸고 두면 커서가 글 끝으로 튄다 — 목록을 세 줄 만들려면 매번 다시 클릭해야
   * 한다. 상태가 반영된 뒤에 자리를 잡아야 하므로 다음 프레임에 맞춘다.
   */
  const apply = (command: (text: string, start: number, end: number) => EditResult) => {
    const textarea = ref.current;
    if (!textarea) return;
    const result = command(value, textarea.selectionStart, textarea.selectionEnd);
    onChange(result.text);
    requestAnimationFrame(() => {
      textarea.focus();
      textarea.setSelectionRange(result.selectionStart, result.selectionEnd);
    });
  };

  /**
   * 이미지를 올리고 그 자리에 넣는다 (#389).
   *
   * **주소를 사람이 옮겨 적지 않는다.** 올린 뒤 주소를 복사해 붙이게 하면 그 과정에서
   * 틀리고, 틀린 것은 저장하고 나서야 보인다.
   */
  const upload = async (file: File) => {
    setUploading(true);
    setUploadError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const { url } = await request<{ url: string }>("/api/v1/attachments", {
        method: "POST",
        auth: true,
        body: form,
      });
      apply((text, start, end) => {
        const mark = `![${file.name}](${url})`;
        return {
          text: `${text.slice(0, start)}${mark}${text.slice(end)}`,
          selectionStart: start + mark.length,
          selectionEnd: start + mark.length,
        };
      });
    } catch (caught) {
      // **폼은 그대로 둔다.** 올리기가 안 돼도 쓰던 글을 잃으면 안 된다.
      setUploadError(caught instanceof ApiError ? caught.message : "이미지를 올리지 못했습니다.");
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  return (
    <div className="space-y-2">
      {/* 좁은 화면에서는 줄이 접힌다. 도구 모음이 가로로 넘치면 못 쓰는 버튼이 생긴다. */}
      <div className="flex flex-wrap gap-1">
        <ToolButton label="굵게" onClick={() => apply((t, s, e) => wrap(t, s, e, "**"))} />
        <ToolButton label="코드" onClick={() => apply((t, s, e) => wrap(t, s, e, "`"))} />
        <ToolButton label="코드 블록" onClick={() => apply((t, s, e) => codeFence(t, s, e))} />
        <ToolButton label="목록" onClick={() => apply((t, s, e) => linePrefix(t, s, e, "- "))} />
        <ToolButton label="제목" onClick={() => apply((t, s, e) => linePrefix(t, s, e, "## "))} />
        <ToolButton
          label={uploading ? "올리는 중…" : "이미지"}
          onClick={() => fileRef.current?.click()}
        />
        <ToolButton
          label={preview ? "미리보기 닫기" : "미리보기"}
          onClick={() => setPreview((it) => !it)}
        />
      </div>
      {/* 버튼이 누르는 진짜 입력. 화면에는 안 보인다. */}
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) void upload(file);
        }}
      />
      {uploadError ? <p className="text-xs text-danger">{uploadError}</p> : null}

      <Textarea
        ref={ref}
        rows={rows}
        className="font-mono text-xs"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        required
      />

      {/*
        **실제 글과 같은 것으로 그린다.** 이 한 줄이 이 부품의 전부다 —
        미리보기가 다른 렌더러를 쓰면 그것은 미리보기가 아니라 다른 화면이다.
      */}
      {preview ? (
        <div className="rounded-lg border border-border p-4">
          <Markdown source={value} />
        </div>
      ) : null}
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
