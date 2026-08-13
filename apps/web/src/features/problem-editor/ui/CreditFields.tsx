"use client";

import { userApi } from "@/entities/user";
import { Badge, Button, Field, Input, Tooltip } from "@/shared/ui";
import { useState } from "react";

interface Person {
  id: number;
  nickname: string;
}

/**
 * 출제자·검수자·출처 (#236).
 *
 * **닉네임으로 찾아 고른다** — id 를 손으로 치게 하지 않는다 (#223 이 같은 문제를 겪었다).
 * 찾는 경로는 멘션 자동완성(#214)과 같은 것을 쓴다. 이름으로 사람을 고르는 일은 하나다.
 */
export function CreditFields({
  setters,
  reviewers,
  sourceLabel,
  sourceUrl,
  onChange,
}: {
  setters: Person[];
  reviewers: Person[];
  sourceLabel: string;
  sourceUrl: string;
  onChange: (patch: {
    setters?: Person[];
    reviewers?: Person[];
    sourceLabel?: string;
    sourceUrl?: string;
  }) => void;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <PersonPicker
        label="출제자"
        picked={setters}
        onChange={(next) => onChange({ setters: next })}
      />
      <PersonPicker
        label="검수자"
        picked={reviewers}
        onChange={(next) => onChange({ reviewers: next })}
      />
      {/*
        **라벨과 링크는 한 쌍이다** — 링크만 있으면 무엇인지 모르고, 라벨만 있으면
        확인할 수 없다. 다만 링크 없는 출처(책·대회 이름)는 있으므로 링크는 비울 수 있다.
      */}
      <Field label="출처">
        <Input
          placeholder="예: 2024 대학생 프로그래밍 대회 (자체 제작이면 비웁니다)"
          value={sourceLabel}
          onChange={(event) => onChange({ sourceLabel: event.target.value })}
        />
      </Field>
      <Field label="출처 링크">
        <Input
          placeholder="https://…"
          value={sourceUrl}
          onChange={(event) => onChange({ sourceUrl: event.target.value })}
        />
      </Field>
    </div>
  );
}

function PersonPicker({
  label,
  picked,
  onChange,
}: {
  label: string;
  picked: Person[];
  onChange: (next: Person[]) => void;
}) {
  const [keyword, setKeyword] = useState("");
  const [found, setFound] = useState<Person[]>([]);

  const search = async (next: string) => {
    setKeyword(next);
    if (next.trim().length < 2) {
      setFound([]);
      return;
    }
    try {
      setFound(await userApi.mentionCandidates(next.trim()));
    } catch {
      setFound([]);
    }
  };

  return (
    <Field label={label}>
      <div className="space-y-2">
        {/* 붙어 있는 사람을 먼저 보인다 — 무엇이 붙었는지 모르면 고칠 수도 없다. */}
        {picked.length > 0 ? (
          <div className="flex flex-wrap gap-1">
            {picked.map((person) => (
              /*
                **`title=` 을 걷었다** (#291 4단계). 브라우저 기본 툴팁은 1초 넘게
                걸리고 키보드로 닿지 않는다 — 그리고 이름은 툴팁이 아니라
                `aria-label` 이 맡아야 한다. 스크린 리더는 시각적 툴팁을 읽지 않는다.
              */
              <Tooltip key={person.id} content="빼기">
                <button
                  type="button"
                  aria-label={`${person.nickname} 빼기`}
                  onClick={() =>
                    onChange(picked.filter((each) => each.id !== person.id))
                  }
                >
                  <Badge tone="info">{person.nickname} ✕</Badge>
                </button>
              </Tooltip>
            ))}
          </div>
        ) : null}

        <Input
          placeholder="닉네임 2글자 이상"
          value={keyword}
          onChange={(event) => void search(event.target.value)}
        />

        {found.length > 0 ? (
          <div className="flex flex-wrap gap-1">
            {found
              .filter((person) => !picked.some((each) => each.id === person.id))
              .map((person) => (
                <Button
                  key={person.id}
                  variant="ghost"
                  className="px-2 py-0.5 text-xs"
                  onClick={() => {
                    onChange([...picked, person]);
                    setKeyword("");
                    setFound([]);
                  }}
                >
                  + {person.nickname}
                </Button>
              ))}
          </div>
        ) : null}
      </div>
    </Field>
  );
}
