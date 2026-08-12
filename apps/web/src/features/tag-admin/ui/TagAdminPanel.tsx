"use client";

import { tagApi } from "@/entities/tag";
import type { Tag } from "@/entities/tag";
import { ApiError } from "@/shared/api";
import { Button, Card, Field, Input, useToast } from "@/shared/ui";
import { useEffect, useState } from "react";

/** 주소는 만든 뒤 바꿀 수 없다 — 링크와 필터 파라미터가 그것을 가리킨다. */
const SLUG_PATTERN = /^[a-z0-9-]{1,60}$/;

/**
 * 태그 만들기 (#232).
 *
 * **API 만 있고 화면이 없으면 있는 줄도 모른다** (#180). 태그는 어드민만 만들 수 있으므로
 * 만드는 자리도 어드민에 있어야 한다.
 */
export function TagAdminPanel() {
  const toast = useToast();
  const [tags, setTags] = useState<Tag[]>([]);
  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () => {
    tagApi.list().then(setTags).catch(() => setTags([]));
  };
  useEffect(load, []);

  const valid = SLUG_PATTERN.test(slug) && name.trim().length > 0;

  const create = async () => {
    setSaving(true);
    try {
      await tagApi.create({ slug, name: name.trim(), description: description.trim() || undefined });
      setSlug("");
      setName("");
      setDescription("");
      load();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "태그를 만들지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <p className="font-medium text-ink">알고리즘 분류(태그)</p>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">
          어떤 기법으로 푸는 문제인지를 나타냅니다. 분야(무엇에 대한 문제인가)와는 다른
          축이라, 분야와 같은 뜻의 태그는 만들지 않습니다. 주소는 만든 뒤 바꿀 수 없습니다.
        </p>
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        <Field label="주소 (소문자·숫자·붙임표)">
          <Input value={slug} placeholder="예: binary-search" onChange={(e) => setSlug(e.target.value)} />
        </Field>
        <Field label="이름">
          <Input value={name} placeholder="예: 이분 탐색" onChange={(e) => setName(e.target.value)} />
        </Field>
      </div>
      <Field label="설명 (비슷한 태그가 둘 생기는 것을 막습니다)">
        <Input
          value={description}
          placeholder="예: 정렬된 범위를 반씩 줄여 가며 찾는 기법"
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>

      <Button onClick={create} disabled={!valid || saving}>
        {saving ? "만드는 중…" : "태그 만들기"}
      </Button>

      {tags.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 border-t border-border pt-3">
          {tags.map((tag) => (
            <span
              key={tag.id}
              title={tag.description ?? undefined}
              className="rounded-full border border-border px-2.5 py-1 text-xs text-ink-muted"
            >
              {tag.name}
              <span className="ml-1 opacity-60">{tag.problemCount}</span>
            </span>
          ))}
        </div>
      ) : null}
    </Card>
  );
}
