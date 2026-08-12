"use client";

import { useState } from "react";
import type { ProblemTag } from "../model/types";

/**
 * 문제에 붙은 태그 (#232).
 *
 * **기본으로 접어 둔다.** 태그는 답의 일부다 — "이 문제는 DP" 를 알고 푸는 것과 모르고
 * 푸는 것은 다른 문제다. 그렇다고 "푼 뒤에만 보여 주기" 를 택하지는 않았다: 그러면
 * 태그로 공부할 문제를 고르는 일 자체가 불가능해진다 (#139 에서 정답 코드에 대해
 * 같은 이유로 ③을 버렸다).
 *
 * 그래서 **보는 것은 언제든 가능하되, 스스로 펼쳐야 한다.** 실수로 눈에 들어오는 일은
 * 없고, 필요한 사람은 한 번 눌러 본다.
 */
export function TagChips({ tags }: { tags: ProblemTag[] }) {
  const [open, setOpen] = useState(false);

  if (tags.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="text-xs text-ink-muted">알고리즘 분류</span>
      {open ? (
        tags.map((tag) => (
          <a
            key={tag.id}
            href={`/problems?tag=${tag.slug}`}
            className="rounded-full border border-border px-2 py-0.5 text-xs text-ink transition hover:border-brand hover:text-brand"
          >
            {tag.name}
          </a>
        ))
      ) : (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="rounded-full border border-dashed border-border px-2 py-0.5 text-xs text-ink-muted transition hover:text-ink"
        >
          {/*
            개수는 보여 준다. 무엇인지는 가리되 **있다는 사실까지 숨기면** 펼칠 이유를
            알 수 없다. 개수만으로는 어떤 기법인지 드러나지 않는다.
          */}
          {tags.length}개 보기 · 풀이 힌트가 될 수 있습니다
        </button>
      )}
    </div>
  );
}
