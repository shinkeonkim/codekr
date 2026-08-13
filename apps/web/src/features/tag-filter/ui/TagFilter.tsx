"use client";

import { Tooltip } from "@/shared/ui";

import { tagApi } from "@/entities/tag";
import type { Tag } from "@/entities/tag";
import { useEffect, useState } from "react";

/**
 * 태그로 문제 거르기 (#232).
 *
 * **여러 개를 고르면 그리고(AND)다.** 고를수록 좁아진다 — 또는이면 고를수록 넓어져
 * 필터 구실을 못 한다. 그 사실을 화면에도 적는다: 두 개를 골랐는데 결과가 늘면
 * 사람은 자기가 잘못 눌렀다고 생각한다.
 *
 * 태그를 화면에 하드코딩하지 않는다. 태그가 늘어도 이 파일은 그대로다 (#232 완료 조건).
 */
export function TagFilter({
  selected,
  onChange,
}: {
  selected: string[];
  onChange: (slugs: string[]) => void;
}) {
  const [tags, setTags] = useState<Tag[]>([]);

  useEffect(() => {
    tagApi.list().then(setTags).catch(() => setTags([]));
  }, []);

  // 붙은 문제가 없는 태그는 고를 수 있어도 빈 결과만 준다. 고른 것은 남겨 둔다 —
  // 사라지면 왜 걸러졌는지 화면에서 알 수 없다.
  const shown = tags.filter((tag) => tag.problemCount > 0 || selected.includes(tag.slug));
  if (shown.length === 0) return null;

  const toggle = (slug: string) =>
    onChange(selected.includes(slug) ? selected.filter((it) => it !== slug) : [...selected, slug]);

  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-2">
        <span className="text-xs font-medium text-ink-muted">알고리즘 분류</span>
        {selected.length > 1 ? (
          <span className="text-[11px] text-ink-muted">여러 개를 고르면 모두 해당하는 문제만 나옵니다</span>
        ) : null}
        {selected.length > 0 ? (
          <button
            type="button"
            onClick={() => onChange([])}
            className="ml-auto text-[11px] text-ink-muted underline-offset-2 hover:underline"
          >
            분류 해제
          </button>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-1.5">
        {shown.map((tag) => {
          const on = selected.includes(tag.slug);
          return (
            /* 설명을 툴팁으로 (#291 4단계, #288) — `title=` 은 키보드로 열리지 않는다. */
            <Tooltip key={tag.id} content={tag.description ?? undefined}>
            <button
              type="button"
              onClick={() => toggle(tag.slug)}
              aria-pressed={on}
              className={`rounded-full border px-2.5 py-1 text-xs transition ${
                on ? "border-brand bg-brand/10 text-brand" : "border-border text-ink-muted hover:text-ink"
              }`}
            >
              {tag.name}
              {/* 개수를 함께 보여 준다 — 없으면 빈 결과를 계속 만난다. */}
              <span className="ml-1 opacity-60">{tag.problemCount}</span>
            </button>
            </Tooltip>
          );
        })}
      </div>
    </div>
  );
}
