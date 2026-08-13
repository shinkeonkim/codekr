"use client";

import { userApi } from "@/entities/user";
import { Textarea } from "@/shared/ui";
import { useEffect, useRef, useState } from "react";
import type { MentionLabel } from "../model/mentionText";
import { activeQuery } from "../model/mentionText";

/**
 * `@` 를 치면 사람을 고르는 입력창 (#214).
 *
 * **사용자는 닉네임을 보고, 저장은 id 로 된다.** 여기서는 `@닉네임` 인 채로 두고,
 * 고른 사람들을 [onPick] 으로 알려 준다 — 제출할 때 그 목록으로 저장 표기를 만든다.
 */
export function MentionTextarea({
  value,
  onChange,
  onPick,
  rows = 3,
  placeholder,
}: {
  value: string;
  onChange: (next: string) => void;
  onPick: (label: MentionLabel) => void;
  rows?: number;
  placeholder?: string;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const [query, setQuery] = useState<string | null>(null);
  const [found, setFound] = useState<MentionLabel[]>([]);

  /*
    질의가 짧으면 **상태를 건드리지 않고** 목록을 비운다.

    효과 안에서 곧바로 상태를 바꾸면 렌더가 한 번 더 돌고, 린트가 그것을 잡는다.
    "지금 보일 목록" 은 질의에서 파생되는 값이라 상태로 둘 이유도 없다.
  */
  const candidates = query !== null && query.length >= 2 ? found : [];

  useEffect(() => {
    // 두 글자부터 찾는다 — 한 글자로 이름 목록을 훑지 못하게 서버도 막는다 (#223).
    if (query === null || query.length < 2) return;
    let cancelled = false;
    userApi
      .mentionCandidates(query)
      .then((matches) => {
        if (!cancelled) setFound(matches);
      })
      .catch(() => {
        if (!cancelled) setFound([]);
      });
    return () => {
      cancelled = true;
    };
  }, [query]);

  const pick = (label: MentionLabel) => {
    const caret = ref.current?.selectionStart ?? value.length;
    const before = value.slice(0, caret);
    const at = before.lastIndexOf("@");
    onChange(`${before.slice(0, at)}@${label.nickname} ${value.slice(caret)}`);
    onPick(label);
    setQuery(null);
  };

  return (
    <div className="relative">
      <Textarea
        ref={ref}
        rows={rows}
        value={value}
        placeholder={placeholder}
        onChange={(event) => {
          onChange(event.target.value);
          setQuery(activeQuery(event.target.value, event.target.selectionStart ?? 0));
        }}
      />
      {candidates.length > 0 ? (
        // 목록은 입력창 아래에 붙는다. 위로 띄우면 좁은 화면에서 잘린다 (#312 와 같은 결).
        <ul className="absolute z-tooltip mt-1 w-full overflow-hidden rounded-lg border border-border bg-surface shadow-lg">
          {candidates.map((candidate) => (
            <li key={candidate.id}>
              <button
                type="button"
                className="block w-full px-3 py-1.5 text-left text-sm text-ink hover:bg-surface-muted"
                onClick={() => pick(candidate)}
              >
                @{candidate.nickname}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
