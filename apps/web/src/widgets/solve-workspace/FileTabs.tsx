"use client";

import type { ProblemFile } from "@/entities/problem";

/**
 * 파일이 여럿인 문제의 탭 (#457, #498).
 *
 * **고칠 수 없는 파일도 보여 준다.** 감추면 "왜 이 함수가 있는지" 를 알 수 없고,
 * 그것을 읽는 것이 문제의 절반이다 — 대신 자물쇠를 붙여 **왜 안 고쳐지는지**를 말한다.
 */
export function FileTabs({
  files,
  active,
  onSelect,
}: {
  files: ProblemFile[];
  active: string;
  onSelect: (name: string) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1 border-b border-border">
      {files.map((file) => (
        <button
          key={file.name}
          type="button"
          onClick={() => onSelect(file.name)}
          aria-current={file.name === active}
          className={
            file.name === active
              ? "rounded-t-md border-b-2 border-brand px-3 py-1.5 text-sm font-medium text-ink"
              : "rounded-t-md px-3 py-1.5 text-sm text-ink-muted hover:text-ink"
          }
        >
          {file.name}
          {file.editable ? null : (
            <span className="ml-1 text-xs" title="이 파일은 고칠 수 없습니다">
              🔒
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
